package com.example;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class SpineAnimationExtractor {
    static class Entry {
        String path;
        List<String> animations;

        Entry(String path, List<String> animations) {
            this.path = path;
            this.animations = animations;
        }
    }

    static class NullAttachmentLoader implements AttachmentLoader {
        @Override
        public RegionAttachment newRegionAttachment(Skin skin, String name, String path) {
			return null;
        }

        @Override
        public MeshAttachment newMeshAttachment(Skin skin, String name, String path) {
			return null;
        }

        @Override
        public BoundingBoxAttachment newBoundingBoxAttachment(Skin skin, String name) {
            return null;
        }

        @Override
        public ClippingAttachment newClippingAttachment(Skin skin, String name) {
            return null;
        }

        @Override
        public PathAttachment newPathAttachment(Skin skin, String name) {
            return null;
        }

        @Override
        public PointAttachment newPointAttachment(Skin skin, String name) {
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar spine-tool.jar <SPINE_folder_path>");
            System.exit(1);
        }

        Path spinePath = Paths.get(args[0]);
        if (!Files.isDirectory(spinePath)) {
            System.err.println("Error: " + args[0] + " is not a directory");
            System.exit(1);
        }

        // 收集所有 .skel 檔案
        List<File> skelFiles = new ArrayList<>();
        Files.walkFileTree(spinePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".skel")) {
                    skelFiles.add(file.toFile());
					System.out.println(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // 儲存條目
        TreeMap<String, Entry> entries = new TreeMap<>();
        SkeletonBinary skeletonBinary = new SkeletonBinary(new NullAttachmentLoader());

        for (File skelFile : skelFiles) {
            try {
                // 計算相對路徑
                String relativePath = spinePath.relativize(skelFile.toPath()).toString().replace('\\', '/');
                String pathWithoutExt = relativePath.substring(0, relativePath.length() - 5);
                String key = pathWithoutExt.replace('/', '_').toUpperCase();

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

        // 輸出 Lua 程式碼
        System.out.println(luaCode);
    }
}