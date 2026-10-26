package dev.startexport;

import net.minecraft.CrashReport;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiApi;
import dev.startexport.exporters.ExportEMI;
import dev.startexport.exporters.ExportFTBQuests;
import dev.startexport.exporters.ExportItemImages;
import dev.startexport.exporters.ExportRecipeImages;
import dev.startexport.utils.FileUtils;
import dev.startexport.utils.ExecutionUtils;
import dev.startexport.utils.FutureUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod(StarTExporter.ID)
public final class StarTExporter {
    public static final String ID = "startexporter";
    public static final Logger LOG = LogUtils.getLogger();
    public static StarTExporter INSTANCE;

    public Path exportDirectory;
    private CompletableFuture<Void> exportFuture;

    public StarTExporter() {
        INSTANCE = this;
        if (StarTExporterConfig.ENABLED) {
            startExport();
        }
    }

    public boolean isExporting() {
        return exportFuture != null;
    }

    public void startExport() {
        if (exportFuture != null) {
            throw new IllegalStateException("Export already running");
        }

        exportDirectory = FileUtils.resolveGameDirPath(StarTExporterConfig.OUTPUT);
        exportFuture = FutureUtils.allOf(
            ExportEMI.waitReady(),
            ExportFTBQuests.waitReady(),
            ExportItemImages.waitReady(),
            ExportRecipeImages.waitReady()
        ).thenApply((_void) -> {
            LOG.info("Export ready to start.");
            return null;
        }).thenCompose((_void) -> FutureUtils.allOf(
            ExportEMI.export(),
            ExportFTBQuests.export(),
            ExportItemImages.export(),
            ExportRecipeImages.export()
        )).thenRun(() -> {
            try {
                FileUtils.saveExportedFile("complete.json", "{\"status\":\"success\"}\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).whenCompleteAsync((_void, exception) -> {
            if (exception != null) {
                LOG.error("Export failed!", exception);
                if (StarTExporterConfig.QUIT) {
                    ExecutionUtils.submitMainThread(() -> {
                        var mc = Minecraft.getInstance();
                        mc.delayCrash(CrashReport.forThrowable(exception, "StarT export failed"));
                    });
                } else {
                    ExecutionUtils.submitMainThread(() -> exportFuture = null);
                }
                return;
            }

            LOG.info("Export finished!");
            if (StarTExporterConfig.QUIT) {
                ExecutionUtils.submitMainThread(() -> Minecraft.getInstance().stop());
            } else {
                ExecutionUtils.submitMainThread(() -> exportFuture = null);
            }
        }, ExecutionUtils.executor());
    }

    public void cancelExport() {
        if (exportFuture == null) {
            throw new IllegalStateException("Export not running!");
        }

        exportFuture.cancel(true);
        exportFuture = null;
    }

    @Mod.EventBusSubscriber(modid = ID, value = Dist.CLIENT)
    public static final class ClientEvents {

        @SubscribeEvent
        public static void commands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                Commands.literal("startexport")
                    .then(Commands.literal("cancel").executes(context -> {
                        var notRunning = ExecutionUtils.submitMainThread(() -> {
                            if (!INSTANCE.isExporting()) return true;
                            INSTANCE.cancelExport();
                            return false;
                        }).join();

                        if (notRunning) {
                            throw new IllegalStateException("Export not running!");
                        }
                        return 1;
                    }))
                    .then(Commands.literal("debug").executes(context -> {
                        var recipeId = "gtceu:assembly_line/wetware_mainframe_uhv";
                        var recipe = EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse(recipeId));
                        if (recipe == null) {
                            context.getSource().sendFailure(Component.literal("EMI recipe " + recipeId + "was not found"));
                            return 0;
                        }

                        var shouldStop = new boolean[]{false};
                        ExecutionUtils.whileOnGuiRender(e -> {
                            ExportRecipeImages.renderRecipe(e.getGuiGraphics(), recipe);
                            return shouldStop[0];
                        });

                        ExecutionUtils.submit(() -> {
                            ExecutionUtils.submitMainThread(() -> shouldStop[0] = true);
                            return null;
                        }, 5, TimeUnit.SECONDS);

                        return 1;
                    }))
                    .executes(context -> {
                        var alreadyRunning = ExecutionUtils.submitMainThread(() -> {
                            if (INSTANCE.isExporting()) return true;
                            INSTANCE.startExport();
                            return false;
                        }).join();

                        if (alreadyRunning) {
                            throw new IllegalStateException("Export already running!");
                        }
                        return 1;
                    }));
        }
    }
}
