package ru.ifmo.rain.daminov.walk;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.IllegalFormatException;

public class RecursiveWalk {
    private static final long FNV_PRIME = 16777619L;
    private static final long FNV_START = 2166136261L;
    private static final long FNV_MOD = 4294967296L;

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.out.println("Expected 2 arguments!");
        } else if (args[0] == null) {
            System.out.println("First argument null, not good(");
        } else if (args[1] == null) {
            System.out.println("Second argument null, not good(");
        } else {
            walk(args, StandardCharsets.UTF_8);
        }
    }

    private static void walk(String[] args, Charset charset) {
        try {
            Path pathInput = Paths.get(args[0]);
            try {

                Path pathOutput = Paths.get(args[1]);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), charset))) {
                    if (Files.notExists(pathOutput)) {
                        try {
                            if (pathOutput.getParent() != null && !Files.isDirectory(pathOutput.getParent())) {
                                Files.createDirectories(pathOutput.getParent());
                            }
                            Files.createFile(pathOutput);
                        } catch (IOException | UnsupportedOperationException | SecurityException e) {
                            System.out.println("Couldn't create output file, " + e.getMessage());
                        }
                    }
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[0]), charset))) {
                        String file;
                        while ((file = reader.readLine()) != null) {
                            try {
                                Path path = Paths.get(file);
                                MyFileVisitor visitor = new MyFileVisitor(writer); // catch the IOException
                                Files.walkFileTree(path, visitor);
                            } catch (InvalidPathException e) {
                                writer.write(String.format("%08x", 0) + " " + file);
                            } catch (IOException e) {
                                System.out.println("Unable to write to output file, " + e.getMessage());
                            }
                        }
                    } catch (FileNotFoundException e) {
                        System.out.println("Unable to open stream for writing because file not found, " + e.getMessage());
                    } catch (SecurityException e) {
                        System.out.println("No access for opening stream for writing, " + e.getMessage());
                    } catch (IOException e) {
                        System.out.println("Unable to read from file, " + e.getMessage());
                    }
                } catch (FileNotFoundException e) {
                    if (!Files.isRegularFile(pathInput)) {
                        System.out.println("Not found such input file, " + e.getMessage());
                    } else {
                        System.out.println("Input file is inaccessible for reading, " + e.getMessage());
                    }

                } catch (IOException e) {
                    System.out.println("Unable to read from input argument, " + e.getMessage());
                } catch (SecurityException e) {
                    System.out.println("Unable to open stream for reading, no access: " + e.getMessage());
                }
            } catch (InvalidPathException e) {
                System.out.println("Invalid output path, " + e.getMessage());
            }
        } catch (InvalidPathException e) {
            System.out.println("Invalid input path, " + e.getMessage());
        }
    }

    public static long getHash(Path path) {
        long hash = FNV_START;
        try (InputStream reader = new FileInputStream(path.toString())) {
            int sz;
            byte[] buffer = new byte[1024];
            while ((sz = reader.read(buffer, 0, 1024)) >= 0) {
                for (int i = 0; i < sz; i++) {
                    hash = (hash * FNV_PRIME) % FNV_MOD ^ (buffer[i] & ((1 << 8) - 1));
                }
            }
        } catch (IOException | SecurityException e) {
            hash = 0;
        }
        return hash;
    }
}
