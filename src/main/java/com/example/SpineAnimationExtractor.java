package com.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.BoneData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SlotData;
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
        List<Float> animationDurations; // 新增：動畫持續時間
        List<String> skins;
        List<String> bones; // 新增：Bone 名稱
        List<String> slots; // 新增：Slot 名稱

        Entry(String path, List<String> animations, List<Float> animationDurations, List<String> skins, List<String> bones, List<String> slots) {
            this.path = path;
            this.animations = animations;
            this.animationDurations = animationDurations;
            this.skins = skins;
            this.bones = bones;
            this.slots = slots;
        }
    }

    private final String spineFolderPath;
    private boolean processingComplete = false;

    public SpineAnimationExtractor(String spineFolderPath) {
        this.spineFolderPath = spineFolderPath;
    }

    // 將駝峰命名轉為下劃線分隔的大寫形式（例如 BigWin_End → BIG_WIN_END）
    private String toSnakeCaseUpper(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        boolean lastWasUpper = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (!lastWasUpper && result.length() > 0 && result.charAt(result.length() - 1) != '_') {
                    result.append('_');
                }
                result.append(c);
                lastWasUpper = true;
            } else if (c == '_') {
                result.append('_');
                lastWasUpper = false;
            } else {
                if (lastWasUpper && result.length() > 1 && result.charAt(result.length() - 2) != '_') {
                    result.insert(result.length() - 1, '_');
                }
                result.append(Character.toUpperCase(c));
                lastWasUpper = false;
            }
        }
        return result.toString();
    }

    // 確保 Lua 表鍵是合法的標識符（處理數字開頭等非法情況）
    private String makeValidLuaKey(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // 如果鍵以數字開頭，前面加 _
        if (Character.isDigit(input.charAt(0))) {
            return "_" + input;
        }
        return input;
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

                // 提取並排序動畫名稱和持續時間
                Array<Animation> animations = skeletonData.getAnimations();
                List<String> animationNames = new ArrayList<>();
                List<Float> animationDurations = new ArrayList<>();
                for (Animation anim : animations) {
                    animationNames.add(anim.getName());
                    animationDurations.add(anim.getDuration());
                }
                List<Integer> sortedIndices = new ArrayList<>();
                for (int i = 0; i < animationNames.size(); i++) {
                    sortedIndices.add(i);
                }
                sortedIndices.sort((a, b) -> animationNames.get(a).compareTo(animationNames.get(b)));
                List<String> sortedAnimationNames = new ArrayList<>();
                List<Float> sortedAnimationDurations = new ArrayList<>();
                for (int index : sortedIndices) {
                    sortedAnimationNames.add(animationNames.get(index));
                    sortedAnimationDurations.add(animationDurations.get(index));
                }

                // 提取並排序皮膚名稱
                Array<Skin> skins = skeletonData.getSkins();
                List<String> skinNames = new ArrayList<>();
                for (Skin skin : skins) {
                    skinNames.add(skin.getName());
                }
                Collections.sort(skinNames);

                // 提取並排序 Bone 名稱
                Array<BoneData> bones = skeletonData.getBones();
                List<String> boneNames = new ArrayList<>();
                for (BoneData bone : bones) {
                    boneNames.add(bone.getName());
                }
                Collections.sort(boneNames);

                // 提取並排序 Slot 名稱
                Array<SlotData> slots = skeletonData.getSlots();
                List<String> slotNames = new ArrayList<>();
                for (SlotData slot : slots) {
                    slotNames.add(slot.getName());
                }
                Collections.sort(slotNames);

                entries.put(key, new Entry(pathWithoutExt, sortedAnimationNames, sortedAnimationDurations, skinNames, boneNames, slotNames));
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

            // 生成 BoneName 字段
            if (data.bones != null && !data.bones.isEmpty()) {
                luaCode.append("        BoneName = {").append(nl);
                for (String bone : data.bones) {
                    String boneKey = bone.toUpperCase();
                    luaCode.append("            ").append(boneKey).append(" = \"").append(bone).append("\",").append(nl);
                }
                luaCode.append("        },").append(nl);
            }

            // 生成 SlotName 字段
            if (data.slots != null && !data.slots.isEmpty()) {
                luaCode.append("        SlotName = {").append(nl);
                for (String slot : data.slots) {
                    String slotKey = makeValidLuaKey(slot.toUpperCase());
                    luaCode.append("            ").append(slotKey).append(" = \"").append(slot).append("\",").append(nl);
                }
                luaCode.append("        },").append(nl);
            }

            // 生成 Skin 字段（處理非法鍵）
            if (data.skins != null && data.skins.stream().anyMatch(skin -> !skin.equalsIgnoreCase("default"))) {
                luaCode.append("        Skin = {").append(nl);
                for (String skin : data.skins) {
                    String skinKey = skin.equalsIgnoreCase("default") ? "DEFAULT" : makeValidLuaKey(skin.toUpperCase());
                    luaCode.append("            ").append(skinKey).append(" = \"").append(skin).append("\",").append(nl);
                }
                luaCode.append("        },").append(nl);
            }

            // 生成 Animation 字段（包含 time）
            luaCode.append("        Animation = {").append(nl);
            for (int i = 0; i < data.animations.size(); i++) {
                String anim = data.animations.get(i);
                float duration = data.animationDurations.get(i);
                luaCode.append("            ").append(toSnakeCaseUpper(anim)).append(" = {").append(nl);
                luaCode.append("                name = \"").append(anim).append("\",").append(nl);
                boolean isLoop = anim.toLowerCase().contains("loop");
                luaCode.append("                isLoop = ").append(isLoop).append(",").append(nl);
                luaCode.append("                time = ").append(String.format(Locale.US, "%.2f", duration)).append(",").append(nl);
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