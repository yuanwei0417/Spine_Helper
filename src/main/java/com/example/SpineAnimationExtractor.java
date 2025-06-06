package com.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.attachments.AtlasAttachmentLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class SpineAnimationExtractor extends ApplicationAdapter {
    static class Entry {
        String path;
        List<String> animations;
        List<String> skins; // 新增皮膚列表

        Entry(String path, List<String> animations, List<String> skins) {
            this.path = path;
            this.animations = animations;
            this.skins = skins;
        }
    }

    private final String spineFolderPath;
    private boolean processingComplete = false;

    public SpineAnimationExtractor(String spineFolderPath) {
        this.spineFolderPath = spineFolderPath;
    }

    @Override
    public void create() {
        try {
            processSpineFiles();
        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace();
        } finally {
            processingComplete = true;
            Gdx.app.exit();
        }
    }

    private void processSpineFiles() throws Exception {
        Path spinePath = Paths.get(spineFolderPath);
        if (!Files.isDirectory(spinePath)) {
            System.err.println("Error: " + spineFolderPath + " is not a directory");
            return;
        }

        List<File> skelFiles = new ArrayList<>();
        Map<String, File> atlasFiles = new HashMap<>();
        Files.walkFileTree(spinePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.toString();
                if (fileName.endsWith(".skel")) {
                    skelFiles.add(file.toFile());
                    System.out.println("Found .skel file: " + file.toFile());
                } else if (fileName.endsWith(".atlas")) {
                    String baseName = file.getFileName().toString().replace(".atlas", "");
                    atlasFiles.put(baseName, file.toFile());
                    System.out.println("Found .atlas file: " + file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        TreeMap<String, Entry> entries = new TreeMap<>();

        for (File skelFile : skelFiles) {
            try {
                String relativePath = spinePath.relativize(skelFile.toPath()).toString().replace('\\', '/');
                String pathWithoutExt = relativePath.substring(0, relativePath.length() - 5);
                String skelBaseName = skelFile.getName().replace(".skel", "");
                String key = skelBaseName.toUpperCase();

                File atlasFile = atlasFiles.get(skelBaseName);
                if (atlasFile == null) {
                    System.err.println("No .atlas file found for " + skelFile + ", skipping...");
                    continue;
                }

                FileHandle atlasHandle = new FileHandle(atlasFile);
                TextureAtlas atlas = new TextureAtlas(atlasHandle);
                AtlasAttachmentLoader attachmentLoader = new AtlasAttachmentLoader(atlas);
                SkeletonBinary skeletonBinary = new SkeletonBinary(attachmentLoader);
                FileHandle skelHandle = new FileHandle(skelFile);
                SkeletonData skeletonData = skeletonBinary.readSkeletonData(skelHandle);

                // 提取並排序動畫名稱
                Array<Animation> animations = skeletonData.getAnimations();
                List<String> animationNames = new ArrayList<>();
                for (Animation anim : animations) {
                    animationNames.add(anim.getName());
                }
                Collections.sort(animationNames);

                // 提取並排序皮膚名稱
                Array<Skin> skins = skeletonData.getSkins();
                List<String> skinNames = new ArrayList<>();
                for (Skin skin : skins) {
                    skinNames.add(skin.getName());
                }
                Collections.sort(skinNames);

                entries.put(key, new Entry(pathWithoutExt, animationNames, skinNames));
            } catch (Exception e) {
                System.err.println("Error processing " + skelFile + ": " + e.getMessage());
            }
        }

        String nl = System.lineSeparator();
        StringBuilder luaCode = new StringBuilder();
        luaCode.append("local SPINE_SETTING = {").append(nl);
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            String key = entry.getKey();
            Entry data = entry.getValue();
            luaCode.append("    ").append(key).append(" = {").append(nl);
            luaCode.append("        Path = SPINE_ROOT .. \"").append(data.path).append("\",").append(nl);

            // 添加 Skin 字段（如果有非 default 皮膚）
            if (data.skins != null && data.skins.stream().anyMatch(skin -> !skin.equalsIgnoreCase("default"))) {
                luaCode.append("        Skin = {").append(nl);
                for (String skin : data.skins) {
                    String skinKey = skin.equalsIgnoreCase("default") ? "DEFAULT" : skin.toUpperCase();
                    luaCode.append("            ").append(skinKey).append(" = \"").append(skin).append("\",").append(nl);
                }
                luaCode.append("        },").append(nl);
            }

            luaCode.append("        Animation = {").append(nl);
            for (String anim : data.animations) {
                luaCode.append("            ").append(anim.toUpperCase()).append(" = {").append(nl);
                luaCode.append("                name = \"").append(anim).append("\",").append(nl);
                boolean isLoop = anim.toLowerCase().contains("loop");
				luaCode.append("                isLoop = ").append(isLoop).append(",").append(nl);
                luaCode.append("            },").append(nl);
            }
            luaCode.append("        },").append(nl);
            luaCode.append("    },").append(nl);
        }
        luaCode.append("}").append(nl);

        Path outputPath = Paths.get(spineFolderPath, "..", "output.lua");
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            fos.write(luaCode.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("Lua code written to: " + outputPath);
        } catch (IOException e) {
            System.err.println("Error writing to output.lua: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar spine-tool.jar <SPINE_folder_path>");
            System.exit(1);
        }

        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.title = "SpineAnimationExtractor";
        config.width = 1;
        config.height = 1;
        config.forceExit = true;
        config.vSyncEnabled = false;
        new LwjglApplication(new SpineAnimationExtractor(args[0]), config);
    }
}