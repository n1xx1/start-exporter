package dev.startexport.utils;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class AsyncImageSaveWorker {

    private final Path root;

    private final ExecutorService workers;
    private final Semaphore capacity;
    private final AtomicReference<Throwable> cancellation = new AtomicReference<>();

    public AsyncImageSaveWorker(Path root, int threads, int queued) {
        this.root = root.toAbsolutePath().normalize();
        workers = Executors.newFixedThreadPool(threads, task -> new Thread(task, "startexport-file"));
        capacity = new Semaphore(threads + queued);
    }

    private void acquireCapacityLock() throws IOException {
        checkCancellation();
        try {
            while (!capacity.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                checkCancellation();
                if (workers.isShutdown()) {
                    throw new IOException("Image writer is closed");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to queue an image", exception);
        }
    }

    public void saveFile(String relative, NativeImage image) throws IOException {
        acquireCapacityLock();

        NativeImage imageCopy = null;
        var submitted = false;

        try {
            imageCopy = new NativeImage(image.getWidth(), image.getHeight(), false);
            imageCopy.copyFrom(image);

            var finalImageCopy = imageCopy;
            workers.execute(() -> {
                try (finalImageCopy) {
                    checkCancellation();

                    var target = root.resolve(relative).normalize();
                    if (!target.startsWith(root)) {
                        throw new IOException("Invalid export path: " + relative);
                    }

                    Files.createDirectories(target.getParent());
                    finalImageCopy.writeToFile(target);
                } catch (Exception exception) {
                    cancellation.compareAndSet(null, exception);
                    throw new RuntimeException(exception);
                } finally {
                    capacity.release();
                }
            });
            submitted = true;
        } finally {
            if (!submitted) {
                try {
                    if (imageCopy != null) {
                        imageCopy.close();
                    }
                } finally {
                    capacity.release();
                }
            }
        }
    }

    public CompletableFuture<Void> join() {
        workers.shutdown();
        var completion = new CompletableFuture<Void>();
        ExecutionUtils.submit(() -> {
            try {
                if (!workers.awaitTermination(10, TimeUnit.MINUTES)) {
                    throw new IOException("File workers did not finish");
                }
                checkCancellation();
                completion.complete(null);
            } catch (Throwable error) {
                completion.completeExceptionally(error);
            }
            return null;
        });
        return completion;
    }

    private void checkCancellation() throws IOException {
        if (cancellation.get() != null) {
            throw new IOException("An export write failed", cancellation.get());
        }
    }
}
