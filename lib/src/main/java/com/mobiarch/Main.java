package com.mobiarch;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        try {
            Files.createDirectory(Paths.get("mail"));
        } catch (FileAlreadyExistsException e) {
            // Directory already exists, which is fine
        }

        var loop = new IOLoop();

        loop.begin();
    }
}
