package com.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.attachments.AtlasAttachmentLoader;

import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class SpineAnimationExtractor extends ApplicationAdapter {
    static class Entry {
        String path;
        List<String> animations;

        Entry(String path, List<String> animations) {
            this.path = path;
            this.animations = animations;
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
            Gdx.app.exit(); // 完成後關閉應用
        }
    }

    private void processSpineFiles() throws Exception {
        Path spinePath = Paths.get(spineFolderPath);
        if (!Files.isDirectory(spinePath)) {
            System.err.println("Error: " + spineFolderPath + " is not a directory");
            return;
        }

        // 收集所有 .skel 和 .atlas 檔案
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

        // 儲存條目
        TreeMap<String, Entry> entries = new TreeMap<>();

        for (File skelFile : skelFiles) {
            try {
                // 計算相對路徑
                String relativePath = spinePath.relativize(skelFile.toPath()).toString().replace('\\', '/');
                String pathWithoutExt = relativePath.substring(0, relativePath.length() - 5);
                String key = pathWithoutExt.replace('/', '_').toUpperCase();

                // 查找對應的 .atlas 檔案
                String skelBaseName = skelFile.getName().replace(".skel", "");
                File atlasFile = atlasFiles.get(skelBaseName);
                if (atlasFile == null) {
                    System.err.println("No .atlas file found for " + skelFile + ", skipping...");
                    continue;
                }

                // 載入 TextureAtlas
                FileHandle atlasHandle = new FileHandle(atlasFile);
                TextureAtlas atlas = new TextureAtlas(atlasHandle);

                // 使用 AtlasAttachmentLoader
                AtlasAttachmentLoader attachmentLoader = new AtlasAttachmentLoader(atlas);
                SkeletonBinary skeletonBinary = new SkeletonBinary(attachmentLoader);

                // 載入骨骼數據
                FileHandle skelHandle = new FileHandle(skelFile);
                SkeletonData skeletonData = skeletonBinary.readSkeletonData(skelHandle);

                // 提取並排序動畫名稱
                Array<Animation> animations = skeletonData.getAnimations();
                List<String> animationNames = new ArrayList<>();
                for (Animation anim : animations) {
                    animationNames.add(anim.getName());
                }
                Collections.sort(animationNames);

                // 儲存條目
                entries.put(key, new Entry(pathWithoutExt, animationNames));
            } catch (Exception e) {
                System.err.println("Error processing " + skelFile + ": " + e.getMessage());
            }
        }

        // 生成 Lua 程式碼
        StringBuilder luaCode = new StringBuilder();
        luaCode.append("local SPINE_SETTING = {\n");
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            String key = entry.getKey();
            Entry data = entry.getValue();
            luaCode.append("    ").append(key).append(" = {\n");
            luaCode.append("        Path = SPINE_ROOT .. \"").append(data.path).append("\",\n");
            luaCode.append("        Animation = {\n");
            for (String anim : data.animations) {
                luaCode.append("            ").append(anim).append(" = {\n");
                luaCode.append("                name = \"").append(anim).append("\",\n");
                luaCode.append("                isLoop = false,\n");
                luaCode.append("            },\n");
            }
            luaCode.append("        },\n");
            luaCode.append("    },\n");
        }
        luaCode.append("}\n");

        // 將 Lua 程式碼寫入 output.lua
        try {
            Path outputPath = Paths.get(spineFolderPath, "output.lua");
            Files.writeString(outputPath, luaCode.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Lua code written to: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error writing to output.lua: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar spine-tool.jar <SPINE_folder_path>");
            System.exit(1);
        }

        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.title = "Spine Animation Extractor";
        config.width = 1; // 最小窗口大小
        config.height = 1;
        config.forceExit = true;
        config.vSyncEnabled = false;
        new LwjglApplication(new SpineAnimationExtractor(args[0]), config);
    }
}