package ru.ifmo.rain.daminov.walk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.IllegalFormatException;

import static ru.ifmo.rain.daminov.walk.RecursiveWalk.getHash;

public class MyFileVisitor extends SimpleFileVisitor<Path> {
    private BufferedWriter writer;

    public MyFileVisitor(BufferedWriter wr) {
        writer = wr;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
        writer.write(String.format("%08x", getHash(path)) + " " + path);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
        writer.write(String.format("%08x", 0) + " " + path);
        return FileVisitResult.CONTINUE;
    }
}
