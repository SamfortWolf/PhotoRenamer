package com.samfort.photorenamer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class FileRenameService {
    private final ExifMetadataService exifService;
    private final FilenameFormatter formatter;
    private final ExecutorService executor;

    public FileRenameService() {
        this.exifService = new ExifMetadataService();
        this.formatter = new FilenameFormatter();
        this.executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    public void renamePhotos(RenameConfig config, ProgressListener listener) {
        List<Path> files = collectFiles(config);
        listener.onStart(files.size());

        List<Future<RenameResult>> futures = new ArrayList<>();
        for (Path file : files) {
            futures.add(executor.submit(() -> processFile(file, config.isDryRun())));
        }

        int renamed = 0, skipped = 0, errors = 0;

        for (Future<RenameResult> future : futures) {
            try {
                RenameResult result = future.get();
                listener.onProgress(result);

                switch (result.getStatus()) {
                    case SUCCESS: renamed++; break;
                    case SKIPPED: skipped++; break;
                    case ERROR: errors++; break;
                }
            } catch (InterruptedException | ExecutionException e) {
                errors++;
                listener.onProgress(RenameResult.error("Unknown", e.getMessage()));
            }
        }

        listener.onComplete(renamed, skipped, errors);
    }

    private List<Path> collectFiles(RenameConfig config) {
        List<Path> files = new ArrayList<>();
        int maxDepth = config.isRecursive() ? Integer.MAX_VALUE : 1;

        try (Stream<Path> stream = Files.walk(config.getTargetFolder(), maxDepth)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSupportedImageFile)
                    .forEach(files::add);
        } catch (IOException e) {
            // Return empty list on error
        }

        return files;
    }

    private boolean isSupportedImageFile(Path path) {
        String filename = path.toString().toLowerCase();
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex < 0) return false;

        String extension = filename.substring(dotIndex);
        return Constants.SUPPORTED_EXTENSIONS.contains(extension);
    }

    RenameResult processFile(Path file, boolean dryRun) {
        try {
            // Extract photo date/time
            Optional<ZonedDateTime> photoTime = exifService.extractPhotoDateTime(file);
            if (photoTime.isEmpty()) {
                return RenameResult.skipped(file.getFileName().toString(), "No EXIF date");
            }

            // Extract metadata
            Map<String, String> metadata = exifService.extractPhotoMetadata(file);

            // Format new filename
            String newBaseName = formatter.formatFilename(photoTime.get(), metadata);
            String extension = formatter.getFileExtension(file);

            // Find unique filename
            Path newPath = findUniqueFilename(file, newBaseName, extension);

            // Perform rename or dry run
            if (dryRun) {
                return RenameResult.success(
                        file.getFileName().toString(),
                        newPath.getFileName().toString()
                );
            } else {
                Files.move(file, newPath, StandardCopyOption.ATOMIC_MOVE);
                return RenameResult.success(
                        file.getFileName().toString(),
                        newPath.getFileName().toString()
                );
            }

        } catch (IOException e) {
            return RenameResult.error(file.getFileName().toString(), e.getMessage());
        } catch (Exception e) {
            return RenameResult.error(
                    file.getFileName().toString(),
                    e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }

    private Path findUniqueFilename(Path originalFile, String baseName, String extension) {
        Path newPath = originalFile.resolveSibling(baseName + extension);

        // If target equals source or doesn't exist, return it
        if (newPath.equals(originalFile) || !Files.exists(newPath)) {
            return newPath;
        }

        // Find unique name with counter
        int counter = 1;
        do {
            newPath = originalFile.resolveSibling(baseName + "_" + counter + extension);
            counter++;
        } while (Files.exists(newPath) && !newPath.equals(originalFile));

        return newPath;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
