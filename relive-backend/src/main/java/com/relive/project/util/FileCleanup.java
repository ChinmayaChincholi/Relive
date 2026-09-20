package com.relive.project.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FileCleanup {

    private FileCleanup() {
    }

    /**
     * Resolves a stored path (relative to baseDir, or absolute) to a real
     * file path, returning null if it is blank or would point outside baseDir.
     */
    public static Path resolveInside(String baseDir, String path) {
        if (path == null || path.isBlank()) return null;
        try {
            Path base = Paths.get(baseDir).normalize().toAbsolutePath();
            Path file = base.resolve(path).normalize();
            return file.startsWith(base) ? file : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Deletes the given files once the CURRENT transaction has committed
     * (immediately if there is no active transaction). If the transaction
     * rolls back, nothing is deleted.
     */
    public static void deleteAfterCommit(Collection<Path> files) {
        if (files == null) return;
        List<Path> toDelete = files.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (toDelete.isEmpty()) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteAll(toDelete);
                }
            });
        } else {
            deleteAll(toDelete);
        }
    }

    private static void deleteAll(List<Path> files) {
        for (Path p : files) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                System.out.println("[FileCleanup] Could not delete " + p + ": " + e.getMessage());
            }
        }
    }
}