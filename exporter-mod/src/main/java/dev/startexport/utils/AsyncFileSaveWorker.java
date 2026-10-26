package dev.startexport.utils;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class AsyncFileSaveWorker implements AutoCloseable {

    private final Path root;

    private final ExecutorService workers;
    private final Semaphore capacity;
    private final AtomicReference<Throwable> cancellation = new AtomicReference<>();

    public AsyncFileSaveWorker(Path root, int threads, int queued) {
        this.root = root.toAbsolutePath().normalize();
        workers = Executors.newFixedThreadPool(threads, task -> new Thread(task, "startexport-file"));
        capacity = new Semaphore(threads + queued);
    }

    public void saveFile(String relative, byte[] bytes) throws IOException {
        checkCancellation();

        workers.submit(() -> {
            checkCancellation();

            try {
                var target = root.resolve(relative).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Invalid export path: " + relative);
                }

                try {
                    while (!capacity.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                        checkCancellation();
                    }

                    Files.createDirectories(target.getParent());
                    Files.write(target, bytes);

                    return null;
                } finally {
                    capacity.release();
                }
            } catch (Exception e) {
                cancellation.compareAndSet(null, e);
            }

            return null;
        });
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
                workers.shutdownNow();
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

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
