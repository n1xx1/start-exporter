package dev.startexport.exporters;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.WidgetGroup;
import dev.startexport.StarTExporter;
import dev.startexport.utils.AsyncFileSaveWorker;
import dev.startexport.utils.ExecutionUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Captures the recipe card, following EMI's screenshot button, before item rendering starts.
 */
public final class ExportRecipeImages {
    private static final int SCALE = 2;

    private static AsyncFileSaveWorker fileSaveWorker;
    private static IntObjectMap<IntObjectMap<TextureTarget>> renderTargetByWH;
    private static IntObjectMap<IntObjectMap<NativeImage>> renderImageByWH;

    private ExportRecipeImages() {
    }

    public static CompletableFuture<Void> waitReady() {
        return ExecutionUtils.wait(ExportRecipeImages::isReady);
    }

    public static boolean isReady() {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.screen == null && mc.getOverlay() == null;
    }

    public static CompletableFuture<Void> export() {
        var mc = Minecraft.getInstance();
        var renderedRecipes = new MutableInt(0);

        return CompletableFuture.runAsync(() -> {
            fileSaveWorker = new AsyncFileSaveWorker(StarTExporter.INSTANCE.exportDirectory, 4, 32);
            renderTargetByWH = new IntObjectHashMap<>();
            renderImageByWH = new IntObjectHashMap<>();

        }, mc).thenCompose(_void -> ExecutionUtils.whileOnGuiRender(event -> {
            var graphics = event.getGuiGraphics();

            graphics.flush();
            var view = RenderSystem.getModelViewStack();
            RenderSystem.backupProjectionMatrix();
            view.pushPose();
            graphics.pose().pushPose();

            try {
                view.setIdentity();
                view.translate(0, 0, -11000);
                RenderSystem.applyModelViewMatrix();
                graphics.pose().setIdentity();

                var deadline = System.nanoTime() + 1_000_000L;
                ExportEMI.RecipeToExport recipeToExport;

                while (System.nanoTime() < deadline && (recipeToExport = ExportEMI.recipeExportQueue.poll()) != null) {
                    saveRecipe(graphics, recipeToExport.path(), recipeToExport.recipe());
                    var count = renderedRecipes.incrementAndGet();
                    if (count % 500 == 0) {
                        StarTExporter.LOG.info("Rendered {}/{} recipes", count, count + ExportEMI.recipeExportQueue.size());
                    }
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } finally {
                graphics.pose().popPose();
                view.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
                mc.getMainRenderTarget().bindWrite(true);
            }

            return ExportEMI.isRecipeExportingDone();
        })).whenCompleteAsync((_void, exception) -> {
            var count = renderedRecipes.getValue();
            if (count % 500 != 0) {
                StarTExporter.LOG.info("Rendered all {} recipes", count);
            }

            renderTargetByWH.values().forEach(m -> m.values().forEach(TextureTarget::destroyBuffers));
            renderTargetByWH = null;
            renderImageByWH.values().forEach(m -> m.values().forEach(NativeImage::close));
            renderImageByWH = null;
        }, mc).thenCompose(_void -> fileSaveWorker.join());
    }

    private static TextureTarget getRenderTarget(int width, int height) {
        return renderTargetByWH.computeIfAbsent(width, (_width) -> new IntObjectHashMap<>()).computeIfAbsent(height, (_height) -> new TextureTarget(width, height, true, false));
    }

    private static NativeImage getRenderImage(int width, int height) {
        return renderImageByWH.computeIfAbsent(width, (_width) -> new IntObjectHashMap<>()).computeIfAbsent(height, (_height) -> new NativeImage(width, height, false));
    }

    private static void saveRecipe(GuiGraphics graphics, String target, EmiRecipe recipe) throws IOException {
        @SuppressWarnings("resource")
        var renderImage = renderRecipeToFramebuffer(graphics, recipe);
        var bytes = renderImage.asByteArray();
        fileSaveWorker.saveFile(target, bytes);
    }

    public static void renderRecipe(GuiGraphics graphics, EmiRecipe recipe) {
        var border = 4;
        var widgets = new WidgetGroup(null, 0, 0, recipe.getDisplayWidth(), recipe.getDisplayHeight());
        recipe.addWidgets(widgets);
        EmiRenderHelper.renderRecipeBackground(recipe, EmiDrawContext.wrap(graphics), 0, 0);

        try {
            graphics.pose().pushPose();
            graphics.pose().translate(border, border, 0);
            for (var widget : widgets.widgets) {
                widget.render(graphics, -1000, -1000, 0);
            }
        } finally {
            graphics.pose().popPose();
        }

        graphics.flush();
    }

    private static NativeImage renderRecipeToFramebuffer(GuiGraphics graphics, EmiRecipe recipe) {
        var width = recipe.getDisplayWidth() + 8;
        var height = recipe.getDisplayHeight() + 8;

        var renderTarget = getRenderTarget(width * SCALE, height * SCALE);
        var renderImage = getRenderImage(width * SCALE, height * SCALE);

        renderTarget.setClearColor(0, 0, 0, 0);
        renderTarget.clear(false);
        renderTarget.bindWrite(true);

        RenderSystem.setProjectionMatrix(
            new Matrix4f().setOrtho(0, width, height, 0, 1000, 21000),
            VertexSorting.ORTHOGRAPHIC_Z
        );

        try {
            renderRecipe(graphics, recipe);
        } catch (Throwable ex) {
            StarTExporter.LOG.warn("Could not render recipe {}", recipe, ex);
        } finally {
            graphics.flush();
        }

        RenderSystem.bindTexture(renderTarget.getColorTextureId());
        renderImage.downloadTexture(0, false);
        renderImage.flipY();
        return renderImage;
    }
}
