package dev.startexport;

import net.minecraft.CrashReport;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;

import com.mojang.logging.LogUtils;
import dev.startexport.exporters.ExportEMI;
import dev.startexport.exporters.ExportFTBQuests;
import dev.startexport.exporters.ExportItems;
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

    private boolean exportRunning;
    public Path exportDirectory;


    public StarTExporter() {
        INSTANCE = this;
        if (StarTExporterConfig.ENABLED) {
            startExport();
        }
    }

    public void startExport() {
        if (exportRunning) {
            throw new IllegalStateException("Export already running");
        }

        exportRunning = true;
        exportDirectory = FileUtils.resolveGameDirPath(StarTExporterConfig.OUTPUT);

        FutureUtils.allOf(
                ExportEMI.isReady(),
                ExportFTBQuests.isReady(),
                ExportItems.isReady()
            )
            .thenApply((_void) -> {
                LOG.info("Export ready to start.");
                return null;
            })
            .thenCompose((_void) -> FutureUtils.allOf(
                ExportEMI.export(),
                ExportFTBQuests.export(),
                ExportItems.export()
            ))
            .thenRun(() -> {
                try {
                    FileUtils.saveExportedFile("complete.json", "{\"status\":\"success\"}\n".getBytes(StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            })
            .whenCompleteAsync((_void, exception) -> {
                if (exception != null) {
                    LOG.error("Export failed!", exception);
                    if (StarTExporterConfig.QUIT) {
                        ExecutionUtils.submitMainThread(() -> {
                            var mc = Minecraft.getInstance();
                            mc.delayCrash(CrashReport.forThrowable(exception, "StarT export failed"));
                        });
                    } else {
                        ExecutionUtils.submitMainThread(() -> exportRunning = false);
                    }
                    return;
                }

                LOG.info("Export finished!");
                if (StarTExporterConfig.QUIT) {
                    ExecutionUtils.submitMainThread(() -> Minecraft.getInstance().stop());
                } else {
                    ExecutionUtils.submitMainThread(() -> exportRunning = false);
                }
            }, ExecutionUtils.executor());
    }

    @Mod.EventBusSubscriber(modid = ID, value = Dist.CLIENT)
    public static final class ClientEvents {

        @SubscribeEvent
        public static void commands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("startexport").executes(context -> {
                boolean alreadyRunning = ExecutionUtils.submitMainThread(() -> {
                    if (INSTANCE.exportRunning) {
                        return true;
                    }
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
