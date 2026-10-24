package dev.startexport.utils;

import java.util.concurrent.CompletableFuture;

public final class FutureUtils {
    private FutureUtils() {}

    public static CompletableFuture<Void> allOf(CompletableFuture<?>... tasks) {
        var completion = new CompletableFuture<Void>();
        for (var task : tasks) {
            task.whenComplete((result, error) -> {
                if (error != null) completion.completeExceptionally(error);
            });
        }
        CompletableFuture.allOf(tasks).whenComplete((result, error) -> {
            if (error == null) completion.complete(null);
        });
        return completion;
    }
}
