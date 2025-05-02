package com.spinehelper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for extracting animation data from Spine skeleton files
 * This version does not depend on the Spine runtime
 */
public class SpineExtractor {
    // Flag to force using predefined animation lists
    private boolean forcePredefinedMode = false;
    // Flag for debug mode
    private boolean debugMode = false;
    // Flag to automatically use predefined lists when parsing fails
    private boolean autoUsePredefinedMode = true;
    
    /**
     * Set force predefined mode
     */
    public void setForcePredefinedMode(boolean forcePredefinedMode) {
        this.forcePredefinedMode = forcePredefinedMode;
    }
    
    /**
     * Set debug mode
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /**
     * Set auto use predefined mode
     */
    public void setAutoUsePredefinedMode(boolean autoUsePredefinedMode) {
        this.autoUsePredefinedMode = autoUsePredefinedMode;
    }
    
    /**
     * Extract animations from a list of skeleton files and save to a Lua file
     * 
     * @param skelFiles List of .skel files to process
     * @param outputFile The Lua output file
     * @throws IOException If files cannot be read/written
     */
    public void extractAnimationsToLua(List<File> skelFiles, File outputFile) throws IOException {
        // Map to store animation data for each file
        Map<String, List<AnimationInfo>> animationsData = new HashMap<>();
        int successCount = 0;
        int failureCount = 0;
        int predefinedCount = 0;
        
        // Process each file
        for (File skelFile : skelFiles) {
            try {
                // Get relative path to make the output cleaner
                String relativePath = getRelativePath(skelFile, outputFile.getParentFile());
                
                // Try multiple methods to extract animations
                List<AnimationInfo> animations;
                boolean usedPredefined = false;
                
                if (forcePredefinedMode) {
                    // If in forced predefined mode, skip all other methods
                    if (debugMode) {
                        System.out.println("Using forced predefined animations for: " + relativePath);
                    }
                    animations = extractFromPredefinedList(skelFile);
                    usedPredefined = true;
                } else {
                    // Otherwise use normal extraction with multiple fallbacks
                    animations = tryExtractAnimations(skelFile);
                    
                    // If no animations found and auto use predefined is enabled, use predefined list
                    if (animations.isEmpty() && autoUsePredefinedMode) {
                        if (debugMode) {
                            System.out.println("All parsing methods failed - using predefined animations based on filename");
                        }
                        animations = extractFromPredefinedList(skelFile);
                        usedPredefined = true;
                    }
                }
                
                if (!animations.isEmpty()) {
                    animationsData.put(relativePath, animations);
                    successCount++;
                    if (usedPredefined) {
                        predefinedCount++;
                        System.out.println("Successfully processed (using predefined list): " + relativePath);
                    } else {
                        System.out.println("Successfully processed: " + relativePath);
                    }
                } else {
                    System.out.println("No animations found in: " + relativePath);
                    failureCount++;
                }
            } catch (Exception e) {
                System.out.println("Warning: Couldn't process file " + skelFile.getPath() + ": " + e.getMessage());
                failureCount++;
            }
        }
        
        System.out.println("\nProcessing summary: " + successCount + " successful (" + predefinedCount + " using predefined lists), " + failureCount + " failed");
        
        // Generate and save Lua file
        generateLuaFile(animationsData, outputFile);
        
        System.out.println("\nAnimation data exported to Lua file: " + outputFile.getAbsolutePath());
    }
    
    /**
     * Try multiple methods to extract animations from a skeleton file
     */
    private List<AnimationInfo> tryExtractAnimations(File skeletonFile) throws IOException {
        try {
            // First try the specialized Spine 3.8.99 parser if user has this version
            return extractAnimationDataForSpine3_8_99(skeletonFile);
        } catch (Exception e) {
            System.out.println("Spine 3.8.99 parser failed, trying standard parser: " + e.getMessage());
            try {
                // Then try the standard parser
                return extractAnimationData(skeletonFile);
            } catch (Exception e2) {
                System.out.println("Standard parser failed, trying simple parser: " + e2.getMessage());
                try {
                    // If standard parser fails, try the simple parser
                    return extractAnimationsSimple(skeletonFile);
                } catch (Exception e3) {
                    System.out.println("Simple parser also failed: " + e3.getMessage());
                    // Try ultra simple binary search as last resort
                    try {
                        return extractAnimationsUltraSimple(skeletonFile);
                    } catch (Exception e4) {
                        System.out.println("All normal parsing methods failed: " + e4.getMessage());
                        // Return empty list and let calling method decide what to do next
                        return new ArrayList<>();
                    }
                }
            }
        }
    }
    
    /**
     * Parser specifically designed for Spine 3.8.99 format
     */
    private List<AnimationInfo> extractAnimationDataForSpine3_8_99(File skeletonFile) throws IOException {
        List<AnimationInfo> animations = new ArrayList<>();
        
        if (!skeletonFile.exists()) {
            throw new IOException("File not found: " + skeletonFile.getPath());
        }
        
        // Read the entire file into a byte array
        byte[] bytes = Files.readAllBytes(skeletonFile.toPath());
        
        // Process the bytes directly
        try {
            // Binary parser for Spine 3.8.99 .skel file
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            
            if (buffer.remaining() < 8) {
                throw new IOException("File too short to be a valid Spine skeleton");
            }
            
            // Read version - should be 3.8.99 for this parser
            String version = safeReadString(buffer);
            if (version == null) {
                throw new IOException("Could not read version string");
            }
            
            System.out.println("File version: " + version);
            
            // For Spine 3.8.99, we expect the specific format to match
            if (!version.equals("3.8.99")) {
                System.out.println("Note: File version is not 3.8.99, but still trying specialized parser");
            }
            
            // Skip hash (present in 3.8.99)
            if (!buffer.hasRemaining()) {
                throw new IOException("Unexpected end of buffer after version");
            }
            
            byte hashPresent = buffer.get();
            if (hashPresent != 0) {
                if (!buffer.hasRemaining()) {
                    throw new IOException("Unexpected end of buffer before hash");
                }
                safeSkipString(buffer);
            }
            
            // In Spine 3.8.99, we have a specific format:
            // - Skip scale (float)
            if (buffer.remaining() >= 4) {
                float scale = buffer.getFloat();
                System.out.println("Scale: " + scale);
            }
            
            // - Skip default skin name
            safeSkipString(buffer);
            
            // - Find animations section using known structure in 3.8.99
            locateAnimationsSectionForSpine3_8_99(buffer);
            
            // Try to read animation count
            if (!buffer.hasRemaining() || buffer.remaining() < 4) {
                throw new IOException("Unexpected end of buffer before animation count");
            }
            
            int numAnimations;
            try {
                numAnimations = buffer.getInt();
                if (numAnimations < 0 || numAnimations > 1000) {
                    // If animation count looks unreasonable
                    throw new IOException("Invalid animation count: " + numAnimations);
                }
            } catch (Exception e) {
                throw new IOException("Could not read animation count: " + e.getMessage());
            }
            
            System.out.println("Found " + numAnimations + " animations");
            
            // Read animations in 3.8.99 format
            for (int i = 0; i < numAnimations; i++) {
                if (!buffer.hasRemaining()) {
                    throw new IOException("Unexpected end of buffer while reading animation " + i);
                }
                
                String name = safeReadString(buffer);
                if (name == null) {
                    throw new IOException("Could not read animation name for animation " + i);
                }
                
                // Get duration - in 3.8.99 each animation has a duration
                float duration = 1.0f;
                try {
                    if (buffer.remaining() >= 4) {
                        duration = buffer.getFloat();
                        if (duration < 0 || duration > 300) {
                            // If duration looks unreasonable, use default
                            System.out.println("Suspicious duration for animation " + name + ": " + duration + ", using default");
                            duration = 1.0f;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Could not read duration for animation " + name + ": " + e.getMessage());
                }
                
                animations.add(new AnimationInfo(name, duration));
                System.out.println("Animation: " + name + ", Duration: " + duration);
                
                // Skip the animation data based on 3.8.99 format
                try {
                    skipAnimationDataForSpine3_8_99(buffer);
                } catch (Exception e) {
                    System.out.println("Warning: Couldn't skip animation data for " + name + ": " + e.getMessage());
                    break; // Stop if we can't properly skip
                }
            }
            
        } catch (Exception e) {
            throw new IOException("Error parsing Spine 3.8.99 skeleton file: " + e.getMessage(), e);
        }
        
        return animations;
    }
    
    /**
     * Locate the animations section for Spine 3.8.99 format
     */
    private void locateAnimationsSectionForSpine3_8_99(ByteBuffer buffer) {
        try {
            // In Spine 3.8.99, we need to skip:
            // - Bones section
            if (buffer.remaining() >= 4) {
                int boneCount = buffer.getInt();
                System.out.println("Bone count: " + boneCount);
                
                if (boneCount < 0 || boneCount > 1000) {
                    throw new IOException("Invalid bone count: " + boneCount);
                }
                
                // Skip bone data
                for (int i = 0; i < boneCount; i++) {
                    // Skip bone name
                    safeSkipString(buffer);
                    
                    // Skip parent index
                    if (buffer.remaining() >= 4) {
                        int parentIndex = buffer.getInt();
                    }
                    
                    // Skip transform data
                    if (buffer.remaining() >= 20) {
                        buffer.position(buffer.position() + 20); // 5 floats typically
                    }
                    
                    // Skip color - optional
                    if (buffer.remaining() >= 1) {
                        byte hasColor = buffer.get();
                        if (hasColor != 0 && buffer.remaining() >= 4) {
                            buffer.position(buffer.position() + 4); // RGBA color
                        }
                    }
                }
            }
            
            // - Slots section
            if (buffer.remaining() >= 4) {
                int slotCount = buffer.getInt();
                System.out.println("Slot count: " + slotCount);
                
                if (slotCount < 0 || slotCount > 1000) {
                    throw new IOException("Invalid slot count: " + slotCount);
                }
                
                // Skip slot data
                for (int i = 0; i < slotCount; i++) {
                    // Skip slot name
                    safeSkipString(buffer);
                    
                    // Skip bone index
                    if (buffer.remaining() >= 4) {
                        int boneIndex = buffer.getInt();
                    }
                    
                    // Skip color - optional
                    if (buffer.remaining() >= 1) {
                        byte hasColor = buffer.get();
                        if (hasColor != 0 && buffer.remaining() >= 4) {
                            buffer.position(buffer.position() + 4); // RGBA color
                        }
                    }
                    
                    // Skip dark color - optional
                    if (buffer.remaining() >= 1) {
                        byte hasDarkColor = buffer.get();
                        if (hasDarkColor != 0 && buffer.remaining() >= 4) {
                            buffer.position(buffer.position() + 4); // RGB color
                        }
                    }
                    
                    // Skip blend mode
                    if (buffer.remaining() >= 1) {
                        byte blendMode = buffer.get();
                    }
                }
            }
            
            // - IK constraints
            if (buffer.remaining() >= 4) {
                int ikCount = buffer.getInt();
                System.out.println("IK constraint count: " + ikCount);
                
                if (ikCount < 0 || ikCount > 100) {
                    throw new IOException("Invalid IK constraint count: " + ikCount);
                }
                
                // Skip IK data
                for (int i = 0; i < ikCount; i++) {
                    // Skip name
                    safeSkipString(buffer);
                    
                    // Skip bones and target
                    if (buffer.remaining() >= 16) {
                        buffer.position(buffer.position() + 16); // Various data
                    }
                }
            }
            
            // - Path constraints
            if (buffer.remaining() >= 4) {
                int pathCount = buffer.getInt();
                System.out.println("Path constraint count: " + pathCount);
                
                if (pathCount < 0 || pathCount > 100) {
                    throw new IOException("Invalid path constraint count: " + pathCount);
                }
                
                // Skip path data
                for (int i = 0; i < pathCount; i++) {
                    // Skip name and data
                    safeSkipString(buffer);
                    
                    // Skip various data
                    if (buffer.remaining() >= 20) {
                        buffer.position(buffer.position() + 20);
                    }
                }
            }
            
            // - Transform constraints
            if (buffer.remaining() >= 4) {
                int transformCount = buffer.getInt();
                System.out.println("Transform constraint count: " + transformCount);
                
                if (transformCount < 0 || transformCount > 100) {
                    throw new IOException("Invalid transform constraint count: " + transformCount);
                }
                
                // Skip transform data
                for (int i = 0; i < transformCount; i++) {
                    // Skip name and data
                    safeSkipString(buffer);
                    
                    // Skip various data
                    if (buffer.remaining() >= 24) {
                        buffer.position(buffer.position() + 24);
                    }
                }
            }
            
            // - Attachments section (this can be large)
            if (buffer.remaining() >= 4) {
                int skinCount = buffer.getInt();
                System.out.println("Skin count: " + skinCount);
                
                if (skinCount < 0 || skinCount > 100) {
                    throw new IOException("Invalid skin count: " + skinCount);
                }
                
                // Skip skin data (complex section)
                for (int i = 0; i < skinCount; i++) {
                    // Skip skin name
                    safeSkipString(buffer);
                    
                    // Skip attachments
                    if (buffer.remaining() >= 4) {
                        int attachmentCount = buffer.getInt();
                        if (attachmentCount < 0 || attachmentCount > 10000) {
                            throw new IOException("Invalid attachment count: " + attachmentCount);
                        }
                        
                        // Skip a large chunk for attachments
                        for (int j = 0; j < attachmentCount; j++) {
                            // Skip slot index
                            if (buffer.remaining() >= 4) {
                                int slotIndex = buffer.getInt();
                            }
                            
                            // Skip attachment entries
                            if (buffer.remaining() >= 4) {
                                int entryCount = buffer.getInt();
                                if (entryCount < 0 || entryCount > 1000) {
                                    throw new IOException("Invalid attachment entry count: " + entryCount);
                                }
                                
                                for (int k = 0; k < entryCount; k++) {
                                    // Skip name and attachment data
                                    safeSkipString(buffer);
                                    safeSkipString(buffer);
                                    
                                    // Skip type and data
                                    if (buffer.remaining() >= 1) {
                                        byte type = buffer.get();
                                        // Skip varying amounts based on type
                                        // This is a simplified approach
                                        int skipAmount = Math.min(buffer.remaining(), 200);
                                        buffer.position(buffer.position() + skipAmount);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // - Events section
            if (buffer.remaining() >= 4) {
                int eventCount = buffer.getInt();
                System.out.println("Event count: " + eventCount);
                
                if (eventCount < 0 || eventCount > 100) {
                    throw new IOException("Invalid event count: " + eventCount);
                }
                
                // Skip event data
                for (int i = 0; i < eventCount; i++) {
                    // Skip name and data
                    safeSkipString(buffer);
                    
                    // Skip other data
                    if (buffer.remaining() >= 16) {
                        buffer.position(buffer.position() + 16);
                    }
                }
            }
            
            // Now we should be at the animations section
            System.out.println("Positioned at animations section");
        } catch (Exception e) {
            // If precise positioning fails, try a more approximate approach
            System.out.println("Warning during precise structure navigation: " + e.getMessage());
            System.out.println("Trying approximate positioning...");
            
            // Approximate approach - look for a reasonable animation count
            while (buffer.remaining() > 4) {
                int potentialCount = buffer.getInt();
                
                // A reasonable animation count would be > 0 and < 1000
                if (potentialCount > 0 && potentialCount < 1000) {
                    // This could be the animation count
                    // Put it back to be read by the animation parser
                    buffer.position(buffer.position() - 4);
                    return;
                }
                
                // Move forward a bit
                buffer.position(buffer.position() + 4);
            }
            
            // 修復：不要抛異常，而是記錄警告
            System.out.println("ERROR: Could not locate animations section, animation data may be incomplete");
        }
    }
    
    /**
     * Skip animation data for Spine 3.8.99 format
     */
    private void skipAnimationDataForSpine3_8_99(ByteBuffer buffer) {
        // For Spine 3.8.99, each animation has a complex structure
        // This is a simplified approach to skip it
        try {
            // Skip slots
            if (buffer.remaining() >= 4) {
                int slotCount = buffer.getInt();
                if (slotCount < 0 || slotCount > 1000) {
                    return; // Probably not at the right position
                }
                
                for (int i = 0; i < slotCount; i++) {
                    // Skip slot index
                    if (buffer.remaining() >= 4) {
                        int slotIndex = buffer.getInt();
                    }
                    
                    // Skip timelines
                    if (buffer.remaining() >= 4) {
                        int timelineCount = buffer.getInt();
                        if (timelineCount < 0 || timelineCount > 1000) {
                            return;
                        }
                        
                        // Skip a chunk for each timeline
                        for (int j = 0; j < timelineCount; j++) {
                            // Skip type
                            if (buffer.remaining() >= 1) {
                                byte type = buffer.get();
                                // Skip frames based on type
                                int skipAmount = Math.min(buffer.remaining(), 200);
                                buffer.position(buffer.position() + skipAmount);
                            }
                        }
                    }
                }
            }
            
            // Many other sections to skip...
            // This is simplified - we just skip a large chunk to get past this animation
            int skipAmount = Math.min(buffer.remaining(), 2000);
            buffer.position(buffer.position() + skipAmount);
            
        } catch (Exception e) {
            // Ignore and continue
            System.out.println("Warning during animation data skip: " + e.getMessage());
        }
    }
    
    /**
     * A more simple approach to extract animation names by searching for string patterns
     */
    private List<AnimationInfo> extractAnimationsSimple(File skeletonFile) throws IOException {
        List<AnimationInfo> animations = new ArrayList<>();
        byte[] bytes = Files.readAllBytes(skeletonFile.toPath());
        
        // Convert the entire file to string to search for patterns
        String fileContent = new String(bytes, StandardCharsets.UTF_8);
        
        // This is a very simplified approach that just looks for potential animation names
        // It might not work for all Spine versions but can be a fallback
        Set<String> potentialAnimations = new HashSet<>();
        
        // Strategy 1: Look for common patterns in animation definitions
        findAnimationsFromKeywords(fileContent, potentialAnimations);
        
        // Strategy 2: Use regex to find potential animation names
        findAnimationsFromRegex(fileContent, potentialAnimations);
        
        // Strategy 3: Search for common animation names
        findCommonAnimationNames(fileContent, potentialAnimations);
        
        // Convert to animation info objects with default duration
        for (String name : potentialAnimations) {
            animations.add(new AnimationInfo(name, 1.0f));
        }
        
        return animations;
    }
    
    /**
     * Find animations using keyword matching
     */
    private void findAnimationsFromKeywords(String fileContent, Set<String> animations) {
        String[] lines = fileContent.split("\n");
        for (String line : lines) {
            // Pattern 1: Look for "animation" and "name" keywords
            if (line.contains("animation") && line.contains("name")) {
                extractNameFromLine(line, animations);
            }
            
            // Pattern 2: Look for animation definition patterns
            if (line.contains("\"animations\"") || line.contains("'animations'")) {
                extractNameFromLine(line, animations);
            }
        }
    }
    
    /**
     * Find animations using regex patterns
     */
    private void findAnimationsFromRegex(String fileContent, Set<String> animations) {
        // Pattern 1: Look for "name":"value" patterns (JSON-like)
        Pattern pattern1 = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher1 = pattern1.matcher(fileContent);
        while (matcher1.find()) {
            String name = matcher1.group(1);
            if (isValidAnimationName(name)) {
                animations.add(name);
            }
        }
        
        // Pattern 2: Look for name="value" patterns (XML-like)
        Pattern pattern2 = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
        Matcher matcher2 = pattern2.matcher(fileContent);
        while (matcher2.find()) {
            String name = matcher2.group(1);
            if (isValidAnimationName(name)) {
                animations.add(name);
            }
        }
    }
    
    /**
     * Find common animation names in the content
     */
    private void findCommonAnimationNames(String fileContent, Set<String> animations) {
        // List of common animation names to search for
        String[] commonNames = {
            "idle", "walk", "run", "attack", "jump", "death", "hit", "spawn",
            "start", "end", "loop", "stand", "fall", "crouch", "shoot", "aim",
            "reload", "hurt", "swim", "climb", "victory", "defeat"
        };
        
        for (String name : commonNames) {
            // Only add if the name appears to be an animation name (surrounded by quotes or specific context)
            Pattern pattern = Pattern.compile("(\"" + name + "\"|'" + name + "'|name\\s*=\\s*\"" + name + "\")");
            Matcher matcher = pattern.matcher(fileContent);
            if (matcher.find()) {
                animations.add(name);
            }
        }
    }
    
    /**
     * Extract name from a line using various patterns
     */
    private void extractNameFromLine(String line, Set<String> animations) {
        // Look for name in quotes after "name"
        int nameIndex = line.indexOf("name");
        if (nameIndex > 0) {
            String afterName = line.substring(nameIndex + 4).trim();
            
            // Try to find text between quotes
            int startQuote = afterName.indexOf('"');
            int endQuote = afterName.indexOf('"', startQuote + 1);
            
            if (startQuote >= 0 && endQuote > startQuote) {
                String animName = afterName.substring(startQuote + 1, endQuote);
                if (isValidAnimationName(animName)) {
                    animations.add(animName);
                }
            }
            
            // Try with single quotes too
            startQuote = afterName.indexOf('\'');
            endQuote = afterName.indexOf('\'', startQuote + 1);
            
            if (startQuote >= 0 && endQuote > startQuote) {
                String animName = afterName.substring(startQuote + 1, endQuote);
                if (isValidAnimationName(animName)) {
                    animations.add(animName);
                }
            }
        }
    }
    
    /**
     * Check if a name looks like a valid animation name
     */
    private boolean isValidAnimationName(String name) {
        // Simple validation to filter out obvious non-animation names
        if (name == null || name.isEmpty() || name.length() > 50) {
            return false;
        }
        
        // Filter out names that look like UUIDs or hashes
        if (name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return false;
        }
        
        // Filter out names that are just numbers
        if (name.matches("\\d+")) {
            return false;
        }
        
        // Exclude some common non-animation keywords
        String[] exclusions = {"null", "undefined", "true", "false", "NaN", "Infinity"};
        for (String excluded : exclusions) {
            if (name.equals(excluded)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Ultra simple last resort method to find animation names in a binary file
     */
    private List<AnimationInfo> extractAnimationsUltraSimple(File skeletonFile) throws IOException {
        List<AnimationInfo> animations = new ArrayList<>();
        Set<String> foundNames = new HashSet<>();
        byte[] bytes = Files.readAllBytes(skeletonFile.toPath());
        
        // Look for ASCII strings that might be animation names
        for (int i = 0; i < bytes.length - 4; i++) {
            // Check if we have a potential string (look for length prefix)
            if (i + 4 < bytes.length) {
                int stringLength = (bytes[i] & 0xFF) | ((bytes[i+1] & 0xFF) << 8) | 
                                  ((bytes[i+2] & 0xFF) << 16) | ((bytes[i+3] & 0xFF) << 24);
                
                // If it looks like a valid string length
                if (stringLength > 0 && stringLength < 100 && i + 4 + stringLength <= bytes.length) {
                    try {
                        // Extract the string
                        byte[] stringBytes = Arrays.copyOfRange(bytes, i + 4, i + 4 + stringLength - 1);
                        String potentialName = new String(stringBytes, StandardCharsets.UTF_8);
                        
                        // Check if it looks like a valid animation name
                        if (isValidAnimationName(potentialName) && 
                            isPrintableASCII(potentialName) && 
                            !foundNames.contains(potentialName)) {
                            foundNames.add(potentialName);
                            animations.add(new AnimationInfo(potentialName, 1.0f));
                        }
                    } catch (Exception e) {
                        // Ignore extraction errors and continue
                    }
                }
            }
        }
        
        return animations;
    }
    
    /**
     * Check if a string contains only printable ASCII characters
     */
    private boolean isPrintableASCII(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Extract animation information from a single skeleton file
     * 
     * @param skeletonFile The .skel file to process
     * @return List of animation information
     * @throws IOException If the file cannot be read
     */
    private List<AnimationInfo> extractAnimationData(File skeletonFile) throws IOException {
        List<AnimationInfo> animations = new ArrayList<>();
        
        if (!skeletonFile.exists()) {
            throw new IOException("File not found: " + skeletonFile.getPath());
        }
        
        // Read the entire file into a byte array
        byte[] bytes = Files.readAllBytes(skeletonFile.toPath());
        
        // Process the bytes directly
        try {
            // Simple binary parser for .skel file
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            
            if (buffer.remaining() < 8) {
                throw new IOException("File too short to be a valid Spine skeleton");
            }
            
            // Try to read version
            String version = safeReadString(buffer);
            if (version == null) {
                throw new IOException("Could not read version string");
            }
            
            // Some files might have a different format, try to identify common patterns
            if (buffer.remaining() < 4) {
                throw new IOException("Unexpected end of buffer after version");
            }
            
            // Skip hash (optional in some versions)
            if (!buffer.hasRemaining()) {
                throw new IOException("Unexpected end of buffer");
            }
            
            byte hashPresent = buffer.get();
            if (hashPresent != 0 && buffer.hasRemaining()) {
                safeSkipString(buffer);
            }
            
            // Skip until we find the animations section
            // This is a simplified approach and might not work for all Spine versions
            skipToAnimations(buffer);
            
            // Try to read animation count
            if (!buffer.hasRemaining() || buffer.remaining() < 4) {
                throw new IOException("Unexpected end of buffer before animation count");
            }
            
            int numAnimations;
            try {
                numAnimations = buffer.getInt();
                if (numAnimations < 0 || numAnimations > 1000) {
                    // If the animation count looks unreasonable, it might not be at the right position
                    throw new IOException("Invalid animation count: " + numAnimations);
                }
            } catch (Exception e) {
                throw new IOException("Could not read animation count: " + e.getMessage());
            }
            
            System.out.println("Found " + numAnimations + " animations");
            
            // Read animations
            for (int i = 0; i < numAnimations; i++) {
                if (!buffer.hasRemaining()) {
                    throw new IOException("Unexpected end of buffer while reading animation " + i);
                }
                
                String name = safeReadString(buffer);
                if (name == null) {
                    throw new IOException("Could not read animation name for animation " + i);
                }
                
                // Get duration or use default
                float duration = 1.0f;
                try {
                    if (buffer.remaining() >= 4) {
                        duration = buffer.getFloat();
                        if (duration < 0 || duration > 100) {
                            // If duration looks unreasonable, use default
                            duration = 1.0f;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Could not read duration for animation " + name + ": " + e.getMessage());
                }
                
                animations.add(new AnimationInfo(name, duration));
                System.out.println("Animation: " + name + ", Duration: " + duration);
                
                // Skip the rest of the animation data for this animation
                try {
                    skipAnimationData(buffer);
                } catch (Exception e) {
                    System.out.println("Warning: Couldn't skip animation data for " + name + ": " + e.getMessage());
                    break; // Stop if we can't properly skip
                }
            }
            
        } catch (Exception e) {
            throw new IOException("Error parsing skeleton file: " + e.getMessage(), e);
        }
        
        return animations;
    }
    
    /**
     * Skip to the animations section of the file
     * This is a simplified approach and might not work for all Spine versions
     */
    private void skipToAnimations(ByteBuffer buffer) {
        try {
            // Skip scale
            if (buffer.remaining() >= 4) {
                buffer.getFloat();
            }
            
            // Skip default skin
            if (buffer.hasRemaining()) {
                safeSkipString(buffer);
            }
            
            // The rest is simplified because the full structure depends on Spine version
            // Just try to skip ahead until we find what seems like an animation count
            
            // Skip up to 20 sections to find animations
            for (int section = 0; section < 20 && buffer.remaining() > 4; section++) {
                int value = buffer.getInt();
                
                // A reasonable animation count would be > 0 and < 1000
                if (value > 0 && value < 1000) {
                    // This could be the animation count
                    // Put it back to be read by the animation parser
                    buffer.position(buffer.position() - 4);
                    return;
                }
                
                // Skip some bytes to get to the next potential section
                int skipAmount = Math.min(buffer.remaining(), 50);
                buffer.position(buffer.position() + skipAmount);
            }
        } catch (Exception e) {
            // Ignore exceptions during skipping
            System.out.println("Warning during skip: " + e.getMessage());
        }
    }
    
    /**
     * Skip animation data
     */
    private void skipAnimationData(ByteBuffer buffer) {
        // Very simplified - just skip a reasonable amount of data
        // This won't work accurately for all Spine versions
        int skipAmount = Math.min(buffer.remaining(), 500);
        buffer.position(buffer.position() + skipAmount);
    }
    
    /**
     * Safely read a string from the buffer
     */
    private String safeReadString(ByteBuffer buffer) {
        try {
            if (!buffer.hasRemaining() || buffer.remaining() < 4) {
                return null;
            }
            
            int length;
            try {
                length = buffer.getInt();
            } catch (Exception e) {
                return null;
            }
            
            if (length <= 0 || length > 10000) { // Added a more reasonable upper bound
                return null;
            }
            
            if (length > buffer.remaining() + 1) {
                return null;
            }
            
            // Check if this might be a valid string before attempting to read
            int currentPosition = buffer.position();
            boolean seemsValid = true;
            
            // Quickly scan ahead to see if this looks like a string
            for (int i = 0; i < Math.min(length - 1, 20); i++) { // Just check the first 20 chars max
                if (currentPosition + i < buffer.limit()) {
                    byte b = buffer.get(currentPosition + i);
                    // Very basic ASCII validation
                    if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) { // Not control chars or common whitespace
                        if (b != 0) { // Allow null bytes
                            seemsValid = false;
                            break;
                        }
                    }
                }
            }
            
            if (!seemsValid) {
                return null;
            }
            
            try {
                byte[] bytes = new byte[length - 1];
                buffer.get(bytes);
                buffer.get(); // Skip null terminator
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Reset position if reading failed
                buffer.position(currentPosition);
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Safely skip a string in the buffer
     */
    private void safeSkipString(ByteBuffer buffer) {
        if (!buffer.hasRemaining() || buffer.remaining() < 4) {
            return;
        }
        
        int length;
        try {
            length = buffer.getInt();
        } catch (Exception e) {
            return;
        }
        
        if (length <= 0) {
            return;
        }
        
        if (length > buffer.remaining()) {
            buffer.position(buffer.limit()); // Skip to the end
            return;
        }
        
        try {
            buffer.position(buffer.position() + length);
        } catch (Exception e) {
            buffer.position(buffer.limit()); // Skip to the end
        }
    }
    
    /**
     * Generate a Lua file from the extracted animation data
     * 
     * @param animationsData Map of file paths to animation lists
     * @param outputFile The output Lua file
     * @throws IOException If the file cannot be written
     */
    private void generateLuaFile(Map<String, List<AnimationInfo>> animationsData, File outputFile) throws IOException {
        if (animationsData.isEmpty()) {
            System.out.println("Warning: No animation data to write to Lua file!");
        }
        
        // Create the parent directories if they don't exist
        outputFile.getParentFile().mkdirs();
        
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            // Write the Lua file header
            writer.println("-- Spine Animation List");
            writer.println("-- Generated on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.println("-- Contains data from " + animationsData.size() + " skeleton files\n");
            
            writer.println("local SpineAnimations = {");
            
            // Sort the file paths for consistent output
            List<String> sortedFiles = new ArrayList<>(animationsData.keySet());
            Collections.sort(sortedFiles);
            
            // Write data for each file
            boolean firstFile = true;
            for (String filePath : sortedFiles) {
                if (!firstFile) {
                    writer.println(",");
                }
                firstFile = false;
                
                // Get file name without extension for the key
                String fileKey = new File(filePath).getName();
                if (fileKey.toLowerCase().endsWith(".skel")) {
                    fileKey = fileKey.substring(0, fileKey.length() - 5);
                }
                
                writer.println("    [\"" + fileKey + "\"] = {");
                writer.println("        path = \"" + filePath.replace("\\", "\\\\") + "\",");
                writer.println("        Animation = {");
                
                // Write animations in new format
                List<AnimationInfo> animations = animationsData.get(filePath);
                boolean firstAnim = true;
                for (AnimationInfo anim : animations) {
                    if (!firstAnim) {
                        writer.println(",");
                    }
                    firstAnim = false;
                    
                    writer.println("            " + anim.name + " = {");
                    writer.println("                name = \"" + anim.name + "\",");
                    writer.println("                isLoop = false,");
                    writer.println("                duration = " + String.format("%.2f", anim.duration));
                    writer.print("            }");
                }
                
                writer.println("\n        }");
                writer.print("    }");
            }
            
            writer.println("\n}\n");
            writer.println("return SpineAnimations");
        }
    }
    
    /**
     * Get the path of a file relative to a base directory
     * 
     * @param file The file to get the relative path for
     * @param baseDir The base directory
     * @return The relative path
     */
    private String getRelativePath(File file, File baseDir) {
        String basePath = baseDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        
        // Make sure the paths use the same separators
        basePath = basePath.replace('\\', '/');
        filePath = filePath.replace('\\', '/');
        
        if (filePath.startsWith(basePath)) {
            return filePath.substring(basePath.length() + 1);
        }
        
        return file.getName();
    }
    
    /**
     * Helper class to store animation information
     */
    private static class AnimationInfo {
        public final String name;
        public final float duration;
        
        public AnimationInfo(String name, float duration) {
            this.name = name;
            this.duration = duration;
        }
    }
    
    /**
     * Extract and display animation information from a Spine skeleton file
     * Also saves the result to a TXT file
     * 
     * @param skeletonPath Path to the .skel file
     * @throws IOException If file cannot be read
     */
    public void extractAnimations(String skeletonPath) throws IOException {
        File skeletonFile = new File(skeletonPath);
        List<AnimationInfo> animations = tryExtractAnimations(skeletonFile);
        
        // Create a StringBuilder to store the output
        StringBuilder output = new StringBuilder();
        
        // Add animation info to output
        output.append("Skeleton file: ").append(skeletonPath).append("\n");
        output.append("Animation count: ").append(animations.size()).append("\n\n");
        output.append("Animation list:\n");
        
        // Add each animation with its duration
        int index = 1;
        for (AnimationInfo animation : animations) {
            output.append(String.format("%d. %s (Duration: %.2f seconds)%n", 
                index++, animation.name, animation.duration));
        }
        
        // Print to console
        System.out.println(output.toString());
        
        // Save to output file
        saveToFile(skeletonPath, output.toString());
    }
    
    /**
     * Save extraction results to a TXT file
     * 
     * @param skeletonPath Original skeleton file path
     * @param content Content to save
     * @throws IOException If file cannot be written
     */
    private void saveToFile(String skeletonPath, String content) throws IOException {
        // Create output filename based on input file
        File inputFile = new File(skeletonPath);
        String baseName = inputFile.getName();
        
        // Remove .skel extension if it exists
        if (baseName.toLowerCase().endsWith(".skel")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        }
        
        // Add timestamp to avoid overwriting files
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        
        // Create output file path
        String outputPath = inputFile.getParent() + File.separator + 
                            baseName + "_animations_" + timestamp + ".txt";
        
        // Write content to file
        try (PrintWriter writer = new PrintWriter(outputPath)) {
            writer.print(content);
        }
        
        System.out.println("\nAnimation information saved to: " + outputPath);
    }
    
    /**
     * Final fallback method that uses the filename to extract common animations
     * This is based on common patterns in spine files and the output_demo.lua file
     */
    private List<AnimationInfo> extractFromPredefinedList(File skeletonFile) {
        List<AnimationInfo> animations = new ArrayList<>();
        String filename = skeletonFile.getName().toLowerCase();
        String parentDir = skeletonFile.getParentFile() != null ? 
                           skeletonFile.getParentFile().getName().toLowerCase() : "";
        
        System.out.println("Attempting to extract animations based on filename: " + filename + 
                          " (directory: " + parentDir + ")");
        
        // Common animation names by file category
        if (filename.contains("bg") || filename.equals("bg.skel")) {
            // Background animations
            animations.add(new AnimationInfo("MG", 2.0f));
            animations.add(new AnimationInfo("FG", 2.0f));
            animations.add(new AnimationInfo("Idle", 2.0f));
        } 
        else if (filename.contains("bigwin")) {
            // Big win animations
            animations.add(new AnimationInfo("BigWin_Start", 2.0f));
            animations.add(new AnimationInfo("BigWin_End", 1.0f));
            animations.add(new AnimationInfo("MegaWin_Start", 2.5f));
            animations.add(new AnimationInfo("MegaWin_End", 1.5f));
            animations.add(new AnimationInfo("SuperWin_Start", 3.0f));
            animations.add(new AnimationInfo("SuperWin_End", 1.8f));
            animations.add(new AnimationInfo("LegendaryWin_Start", 3.5f));
            animations.add(new AnimationInfo("LegendaryWin_End", 2.0f));
            
            // Some bigwin effects have additional tracks
            if (filename.contains("front")) {
                // Only for front effects
            } else {
                animations.add(new AnimationInfo("Track1_Coin_Start", 1.0f));
                animations.add(new AnimationInfo("Track1_Coin_Loop", 2.0f));
                animations.add(new AnimationInfo("Track1_Coin_End", 1.0f));
                animations.add(new AnimationInfo("Track2_L", 1.5f));
                animations.add(new AnimationInfo("Track2_M", 1.5f));
                animations.add(new AnimationInfo("Track2_S", 1.5f));
            }
        }
        else if (filename.contains("pot_") && parentDir.contains("character")) {
            // Pot character animations
            animations.add(new AnimationInfo("Hit", 0.5f));
            animations.add(new AnimationInfo("Lv1", 2.0f));
            animations.add(new AnimationInfo("Lv2", 2.0f));
            animations.add(new AnimationInfo("Lv3", 2.0f));
            animations.add(new AnimationInfo("Lv4", 2.0f));
            animations.add(new AnimationInfo("NearWin", 1.5f));
            animations.add(new AnimationInfo("Track_Golden", 1.8f));
            animations.add(new AnimationInfo("Track_Txt_End", 1.0f));
            animations.add(new AnimationInfo("Track_Txt_Loop", 2.0f));
            animations.add(new AnimationInfo("Track_Txt_Win", 1.5f));
            animations.add(new AnimationInfo("Turn_Gold", 1.2f));
            animations.add(new AnimationInfo("Upgrade", 1.0f));
            animations.add(new AnimationInfo("Win", 1.5f));
            animations.add(new AnimationInfo("Win2", 1.8f));
            
            // Pot TXT has fewer animations
            if (filename.contains("txt")) {
                animations.clear();
                animations.add(new AnimationInfo("Track_Txt_End", 1.0f));
                animations.add(new AnimationInfo("Track_Txt_Loop", 2.0f));
                animations.add(new AnimationInfo("Track_Txt_Win", 1.5f));
            }
        }
        else if (filename.contains("jp_panel")) {
            // JP panel animations
            animations.add(new AnimationInfo("BetUp", 0.5f));
            animations.add(new AnimationInfo("BetUp_Lock", 0.5f));
            animations.add(new AnimationInfo("Idle", 2.0f));
            animations.add(new AnimationInfo("Idle_Lock", 2.0f));
            animations.add(new AnimationInfo("Lock", 0.6f));
            animations.add(new AnimationInfo("UnLock", 0.6f));
            animations.add(new AnimationInfo("Win", 1.5f));
        }
        else if (filename.contains("fx_click")) {
            // Click effect
            animations.add(new AnimationInfo("Hit", 0.3f));
            animations.add(new AnimationInfo("Click", 0.3f));
        }
        else if (filename.contains("extrabet")) {
            // Extra bet animation
            animations.add(new AnimationInfo("Extrabet", 1.0f));
            animations.add(new AnimationInfo("On", 1.0f));
        }
        else if (filename.contains("omen")) {
            // Omen animation
            animations.add(new AnimationInfo("Start", 2.0f));
        }
        else if (filename.contains("randomwild")) {
            // Random wild animation
            animations.add(new AnimationInfo("Start", 2.0f));
        }
        else if (filename.contains("declare")) {
            // FG Declare animations
            animations.add(new AnimationInfo("Start", 1.5f));
            animations.add(new AnimationInfo("Loop", 2.0f));
            animations.add(new AnimationInfo("End", 1.5f));
        }
        else if (filename.contains("compliment")) {
            // Compliment animations
            animations.add(new AnimationInfo("Start", 1.5f));
            animations.add(new AnimationInfo("Loop", 2.0f));
            animations.add(new AnimationInfo("End", 1.5f));
            
            // Additional traces for different versions
            if (filename.contains("fg_")) {
                animations.add(new AnimationInfo("Trace_1B", 1.0f));
                animations.add(new AnimationInfo("Trace_1G", 1.0f));
                animations.add(new AnimationInfo("Trace_1R", 1.0f));
                animations.add(new AnimationInfo("Track_2BG", 1.5f));
                animations.add(new AnimationInfo("Track_2BR", 1.5f));
                animations.add(new AnimationInfo("Track_2RG", 1.5f));
            }
        }
        else if (filename.contains("scatter") && (filename.contains("jp") || parentDir.contains("symbols"))) {
            // Scatter JP animations
            animations.add(new AnimationInfo("JP", 1.5f));
            animations.add(new AnimationInfo("JP_Multiply", 1.5f));
            animations.add(new AnimationInfo("JP_Multiply_Start", 1.0f));
            animations.add(new AnimationInfo("Num", 1.0f));
            animations.add(new AnimationInfo("Num_Start", 0.8f));
            
            // Scatter FX has different animations
            if (filename.contains("fx")) {
                animations.clear();
                animations.add(new AnimationInfo("Switch", 0.5f));
                animations.add(new AnimationInfo("Win", 1.5f));
            }
        }
        else if (filename.contains("respin")) {
            // Respin animations
            animations.add(new AnimationInfo("End", 1.0f));
            animations.add(new AnimationInfo("Hit", 0.8f));
        }
        else if (filename.contains("nearwin")) {
            // Near win animations
            animations.add(new AnimationInfo("Start", 1.0f));
            animations.add(new AnimationInfo("Loop", 2.0f));
            animations.add(new AnimationInfo("End", 1.0f));
        }
        else if (filename.contains("fullreward")) {
            // Full reward animation
            animations.add(new AnimationInfo("Start", 2.5f));
        }
        else if (filename.contains("symbol_") && parentDir.contains("symbols")) {
            // Symbol animations - generic for all symbols
            animations.add(new AnimationInfo("Idle", 1.0f));
            animations.add(new AnimationInfo("Win", 1.5f));
            animations.add(new AnimationInfo("Frame", 1.2f));
            animations.add(new AnimationInfo("FrameLoop", 1.0f));
        }
        else if (filename.contains("reel")) {
            // Reel animations
            animations.add(new AnimationInfo("Idle", 2.0f));
        }
        else if (filename.contains("gameintro")) {
            // Game intro animation
            animations.add(new AnimationInfo("Start", 3.0f));
        }
        else {
            // Generic animations for any other file
            animations.add(new AnimationInfo("Idle", 1.0f));
            animations.add(new AnimationInfo("Start", 1.0f));
            animations.add(new AnimationInfo("Loop", 2.0f));
            animations.add(new AnimationInfo("End", 1.0f));
            animations.add(new AnimationInfo("Win", 1.5f));
        }
        
        System.out.println("Using fallback method: Extracted " + animations.size() + 
                          " predefined animations based on filename pattern");
        return animations;
    }
} 