package dev.startexport.utils;

import net.minecraft.client.Minecraft;

import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ExecutionUtils {

    private static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(4);

    public static ScheduledExecutorService executor() {
        return executorService;
    }

    public static <T> CompletableFuture<T> submit(Callable<T> callable) {
        var future = new CompletableFuture<T>();
        executorService.submit(() -> {
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static <T> CompletableFuture<T> submit(Callable<T> callable, long delay, TimeUnit unit) {
        var future = new CompletableFuture<T>();
        executorService.schedule(() -> {
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, delay, unit);
        return future;
    }

    public static CompletableFuture<Void> submitMainThread(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, Minecraft.getInstance());
    }

    public static <T> CompletableFuture<T> submitMainThread(Callable<T> callable) {
        return CompletableFuture.runAsync(() -> {}, Minecraft.getInstance()).thenCompose((_void) -> {
            try {
                return CompletableFuture.completedFuture(callable.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    public static CompletableFuture<Void> wait(BooleanSupplier supplier) {
        var future = new CompletableFuture<Void>();
        var listener = (Consumer<TickEvent.ClientTickEvent>) event -> {
            if (event.phase != TickEvent.Phase.END || future.isDone()) return;
            try {
                if (supplier.getAsBoolean()) {
                    future.complete(null);
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        future.whenComplete((result, error) -> {
            MinecraftForge.EVENT_BUS.unregister(listener);
        });
        return future;
    }

    public interface OnRender {
        boolean call(RenderGuiEvent.Post event);
    }

    public static CompletableFuture<Void> whileOnRender(OnRender callback) {
        var future = new CompletableFuture<Void>();
        var listener = (Consumer<RenderGuiEvent.Post>) event -> {
            if (future.isDone()) return;
            try {
                if (callback.call(event)) {
                    future.complete(null);
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        future.whenComplete((result, error) -> {
            MinecraftForge.EVENT_BUS.unregister(listener);
        });
        return future;
    }

}
