package com.spinehelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class for the Spine Animation Extractor
 * This is the entry point of the application
 */
public class Main {
    // Debug mode flag
    private static boolean debugMode = false;
    // Force predefined animation list mode
    private static boolean forcePredefinedMode = false;
    // Flag to auto use predefined list if parsing fails
    private static boolean autoUsePredefinedMode = true;
    
    public static void main(String[] args) {
        // Check if debug flag is in arguments
        parseCommandLineOptions(args);
        
        if (args.length < 1 || (args.length == 1 && (args[0].equals("--debug") || args[0].equals("--force-predefined")))) {
            printUsage();
            System.exit(1);
        }

        // Get the actual folder path (excluding parameter flags)
        String folderPath = getFolderPathFromArgs(args);
        
        File folder = new File(folderPath);
        
        if (!folder.exists()) {
            System.out.println("Error: The provided path does not exist: " + folderPath);
            System.exit(1);
        }
        
        if (!folder.isDirectory()) {
            System.out.println("Error: The provided path is not a valid directory: " + folderPath);
            System.out.println("Please drag a folder containing .skel files, not individual files.");
            System.exit(1);
        }

        System.out.println("Scanning for .skel files in: " + folderPath);
        if (debugMode) {
            System.out.println("Debug mode enabled - detailed processing information will be displayed");
        }
        if (forcePredefinedMode) {
            System.out.println("Force predefined mode enabled - using predefined animation lists based on filenames");
        }
        if (autoUsePredefinedMode) {
            System.out.println("Auto predefined mode enabled - will fallback to predefined animations if parsing fails");
        }
        
        try {
            // Find all .skel files in the directory and subdirectories
            List<File> skelFiles = findSkelFiles(folder);
            
            if (skelFiles.isEmpty()) {
                System.out.println("No .skel files found in the directory.");
                if (debugMode) {
                    listDirectoryContents(folder, 0);
                }
                System.exit(0);
            }
            
            System.out.println("Found " + skelFiles.size() + " .skel files:");
            if (debugMode) {
                // List all found files
                for (File file : skelFiles) {
                    System.out.println(" - " + file.getAbsolutePath());
                    try {
                        // Show file size and basic info
                        System.out.println("   Size: " + Files.size(file.toPath()) + " bytes");
                    } catch (IOException e) {
                        System.out.println("   Could not read file info: " + e.getMessage());
                    }
                }
            }
            
            // Process all found .skel files and generate a Lua file
            File outputFile = new File(folder, "output.lua");
            System.out.println("Output will be saved to: " + outputFile.getAbsolutePath());
            
            SpineExtractor extractor = new SpineExtractor();
            extractor.setForcePredefinedMode(forcePredefinedMode);
            extractor.setAutoUsePredefinedMode(autoUsePredefinedMode);
            
            // Set more verbose in debug mode
            if (debugMode) {
                System.out.println("\nStarting animation data extraction...");
                extractor.setDebugMode(true);
            }
            
            // Run extraction
            extractor.extractAnimationsToLua(skelFiles, outputFile);
            
            System.out.println("\nProcess completed successfully!");
            System.out.println("You can use the output.lua file in your project to access all animation data.");
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            } else {
                System.out.println("Run with --debug parameter to see detailed error information.");
            }
            System.exit(1);
        }
    }
    
    /**
     * Print usage information
     */
    private static void printUsage() {
        System.out.println("Usage: java -cp build/classes com.spinehelper.Main <spine_folder_path> [options]");
        System.out.println("Options:");
        System.out.println("  --debug                  Enable detailed debug information");
        System.out.println("  --force-predefined       Force use of predefined animation lists based on filenames");
        System.out.println("\nExample: java -cp build/classes com.spinehelper.Main D:\\Games\\SpineFiles --debug");
    }
    
    /**
     * Parse command line options
     */
    private static void parseCommandLineOptions(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--debug")) {
                debugMode = true;
            } else if (args[i].equals("--force-predefined")) {
                forcePredefinedMode = true;
            }
        }
    }
    
    /**
     * Get the folder path from arguments, ignoring option flags
     */
    private static String getFolderPathFromArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                return args[i];
            }
        }
        return null; // Should never reach here if usage check is done first
    }
    
    /**
     * Recursively list directory contents (debug mode only)
     */
    private static void listDirectoryContents(File directory, int level) {
        if (level == 0) {
            System.out.println("\nDirectory contents view (debug only):");
        }
        
        String indent = " ".repeat(level * 2);
        System.out.println(indent + "📁 " + directory.getName() + "/");
        
        File[] files = directory.listFiles();
        if (files == null) {
            System.out.println(indent + "  ⚠️ Could not read directory contents");
            return;
        }
        
        int fileCount = 0;
        int dirCount = 0;
        
        for (File file : files) {
            if (file.isDirectory()) {
                dirCount++;
                listDirectoryContents(file, level + 1);
            } else {
                fileCount++;
                String extension = getFileExtension(file);
                System.out.println(indent + "  📄 " + file.getName() + 
                                   (extension.isEmpty() ? "" : " [" + extension + "]"));
            }
        }
        
        if (fileCount == 0 && dirCount == 0) {
            System.out.println(indent + "  (Empty directory)");
        }
    }
    
    /**
     * Get file extension
     */
    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return name.substring(lastIndexOf + 1).toLowerCase();
    }
    
    /**
     * Recursively find all .skel files in a directory and its subdirectories
     * 
     * @param directory The directory to search in
     * @return A list of .skel files
     */
    private static List<File> findSkelFiles(File directory) {
        List<File> skelFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    skelFiles.addAll(findSkelFiles(file));
                } else if (file.getName().toLowerCase().endsWith(".skel")) {
                    if (debugMode) {
                        System.out.println("Found file: " + file.getAbsolutePath());
                    }
                    skelFiles.add(file);
                } else if (debugMode) {
                    // Show skipped files in debug mode
                    System.out.println("Skipping non-.skel file: " + file.getName());
                }
            }
        } else if (debugMode) {
            System.out.println("Warning: Could not list files in directory: " + directory.getAbsolutePath());
        }
        
        return skelFiles;
    }
} 