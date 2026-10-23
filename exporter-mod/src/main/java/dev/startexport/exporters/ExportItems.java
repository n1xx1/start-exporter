package dev.startexport.exporters;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.EmiRenderHelper;
import dev.startexport.StarTExporter;
import dev.startexport.utils.AsyncFileSaveWorker;
import dev.startexport.utils.ExecutionUtils;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ExportItems {

    private static final int EXPORTED_IMAGE_SIZE = 32;

    private static Matrix4f screenScaleMatrix;
    private static final Matrix4f pixelScaleMatrix = new Matrix4f().scaling(1.f / 16.f, 1.f / 16.f, 1);
    private static final Matrix4f flipYMatrix = new Matrix4f().scaling(1, -1, 1);
    private static AsyncFileSaveWorker fileSaveWorker;
    private static TextureTarget renderTarget;
    private static IntObjectMap<NativeImage> imageBySize;
    private static NativeImage renderImage;

    public static CompletableFuture<Void> isReady() {
        return ExecutionUtils.wait(ExportItems::isWorldLoaded);
    }

    public static boolean isWorldLoaded() {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.screen == null && mc.getOverlay() == null;
    }

    public static CompletableFuture<Void> export() {
        return CompletableFuture.runAsync(() -> {
            var mc = Minecraft.getInstance();
            fileSaveWorker = new AsyncFileSaveWorker(StarTExporter.INSTANCE.exportDirectory, 4, 32);
            renderTarget = new TextureTarget(EXPORTED_IMAGE_SIZE, EXPORTED_IMAGE_SIZE, true, false);
            imageBySize = new IntObjectHashMap<>();
            renderImage = new NativeImage(EXPORTED_IMAGE_SIZE, EXPORTED_IMAGE_SIZE, false);

            var guiScale = (float) mc.getWindow().getGuiScale();
            var width = mc.getWindow().getWidth() / guiScale;
            var height = mc.getWindow().getHeight() / guiScale;
            screenScaleMatrix = new Matrix4f().scaling(width, height, 1);

        }, Minecraft.getInstance()).thenCompose((_void) ->
            ExecutionUtils.whileOnRender((event) -> {
                var mc = Minecraft.getInstance();
                var graphics = event.getGuiGraphics();

                graphics.pose().pushPose();
                graphics.pose().setIdentity();

                graphics.pose().mulPoseMatrix(screenScaleMatrix);
                graphics.pose().translate(0.5, -0.5, 0);
                graphics.pose().mulPoseMatrix(pixelScaleMatrix);
                graphics.pose().translate(-8, 8, 1);

                Lighting.setupFor3DItems();

                try {
                    ExportEMI.ItemToExport itemToExport;
                    while ((itemToExport = ExportEMI.itemsExportQueue.poll()) != null) {
                        saveItem(graphics, itemToExport.path(), itemToExport.stack(), itemToExport.animationData());
                    }

                    ExportEMI.FluidToExport fluidToExport;
                    while ((fluidToExport = ExportEMI.fluidsExportQueue.poll()) != null) {
                        saveFluid(graphics, fluidToExport.path(), fluidToExport.stack(), fluidToExport.animationData());
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                graphics.pose().popPose();
                mc.getMainRenderTarget().bindWrite(true);

                return ExportEMI.isExportingDone();
            })).thenCompose((_void) -> fileSaveWorker.join());
    }

    private static NativeImage getSizedNativeImage(int height) {
        return imageBySize.computeIfAbsent(height, _height -> new NativeImage(EXPORTED_IMAGE_SIZE, EXPORTED_IMAGE_SIZE * height, false));
    }

    private static void saveItem(GuiGraphics graphics, String target, ItemStack itemStack, @Nullable AnimationData animationData) throws IOException {
        byte[] bytes;
        if (animationData != null) {
            var exportImage = getSizedNativeImage(animationData.frames.size());

            for (int index = 0; index < animationData.frames.size(); index++) {
                animationData.upload(animationData.frames.getInt(index));
                renderItem(graphics, itemStack);
                renderImage.copyRect(
                    exportImage,
                    0, 0, 0, index * EXPORTED_IMAGE_SIZE,
                    32, 32, false, false
                );
            }
            bytes = exportImage.asByteArray();
        } else {
            renderItem(graphics, itemStack);
            bytes = renderImage.asByteArray();
        }

        fileSaveWorker.saveFile(target, bytes);
    }

    private static void renderItem(GuiGraphics graphics, ItemStack itemStack) {
        renderTarget.setClearColor(0, 0, 0, 0);
        renderTarget.clear(false);
        renderTarget.bindWrite(true);

        var mc = Minecraft.getInstance();
        var itemRenderer = mc.getItemRenderer();
        var bakedModel = itemRenderer.getModel(itemStack, null, null, 42);

        graphics.pose().pushPose();
        graphics.pose().translate(8, 8, 150);
        graphics.pose().mulPoseMatrix(flipYMatrix);
        graphics.pose().scale(16, 16, 16);

        if (!bakedModel.usesBlockLight()) {
            Lighting.setupForFlatItems();
        }

        itemRenderer.render(itemStack, ItemDisplayContext.GUI, false,
            graphics.pose(), graphics.bufferSource(), 15728880,
            OverlayTexture.NO_OVERLAY, bakedModel);
        graphics.flush();

        if (!bakedModel.usesBlockLight()) {
            Lighting.setupFor3DItems();
        }

        graphics.pose().popPose();

        RenderSystem.bindTexture(renderTarget.getColorTextureId());
        renderImage.downloadTexture(0, false);
        renderImage.flipY();
    }

    private static void saveFluid(GuiGraphics graphics, String target, FluidStack fluidStack, @Nullable AnimationData animationData) throws IOException {
        byte[] bytes;
        if (animationData != null) {
            var exportImage = getSizedNativeImage(animationData.frames.size());
            for (int index = 0; index < animationData.frames.size(); index++) {
                animationData.upload(animationData.frames.getInt(index));
                renderFluid(graphics, fluidStack);
                renderImage.copyRect(
                    exportImage,
                    0, 0, 0, index * EXPORTED_IMAGE_SIZE,
                    32, 32, false, false
                );
            }
            bytes = exportImage.asByteArray();
        } else {
            renderFluid(graphics, fluidStack);
            bytes = renderImage.asByteArray();
        }

        fileSaveWorker.saveFile(target, bytes);
    }

    private static void renderFluid(GuiGraphics graphics, FluidStack fluidStack) {
        renderTarget.setClearColor(0, 0, 0, 0);
        renderTarget.clear(false);
        renderTarget.bindWrite(true);

        var mc = Minecraft.getInstance();
        var ext = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        var texture = ext.getStillTexture(fluidStack);
        var color = ext.getTintColor(fluidStack);
        var atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        var sprite = atlas.getSprite(texture);
        atlas.bind();

        EmiRenderHelper.drawTintedSprite(
            graphics.pose(),
            sprite,
            color,
            0,
            0,
            0,
            0,
            16,
            16
        );
        graphics.flush();
        RenderSystem.bindTexture(renderTarget.getColorTextureId());
        renderImage.downloadTexture(0, false);
        renderImage.flipY();
    }

    private static final RandomSource RANDOM_SOURCE = RandomSource.create();

    public static @Nullable AnimationData getAnimationDataForItem(ItemStack itemStack) {
        TextureAtlasSprite animatedSprite = null;
        var model = Minecraft.getInstance().getItemRenderer().getModel(itemStack, null, null, 0);
        if (!model.isCustomRenderer()) {
            for (var pass : model.getRenderPasses(itemStack, true)) {
                RANDOM_SOURCE.setSeed(42);
                @SuppressWarnings("deprecation")
                var quads = pass.getQuads(null, null, RANDOM_SOURCE);
                for (var quad : quads) {
                    var sprite = quad.getSprite();
                    if (sprite == animatedSprite) {
                        continue;
                    }
                    @SuppressWarnings("resource")
                    var frames = sprite.contents().getUniqueFrames().count();
                    if (frames <= 1) {
                        continue;
                    }
                    if (animatedSprite != null) {
                        // can't have more than 1 animatedSprite, fallback to no animation
                        return null;
                    }
                    animatedSprite = sprite;
                }
            }
        }
        if (animatedSprite == null) {
            return null;
        }
        return AnimationData.get(animatedSprite);
    }

    public static @Nullable AnimationData getAnimationDataForFluid(FluidStack fluidStack) {
        var mc = Minecraft.getInstance();
        var ext = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        var texture = ext.getStillTexture(fluidStack);
        var atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        var sprite = atlas.getSprite(texture);
        return AnimationData.get(sprite);
    }

    public static class AnimationData {
        public final TextureAtlas atlas;
        public final TextureAtlasSprite sprite;
        public final SpriteContents.AnimatedTexture animatedTexture;
        public List<IntIntPair> data;
        public IntList frames;

        public static @Nullable AnimationData get(TextureAtlasSprite sprite) {
            @SuppressWarnings("resource")
            var contents = sprite.contents();
            var animatedTexture = contents.animatedTexture;
            if (animatedTexture == null) {
                return null;
            }
            return new AnimationData(sprite, animatedTexture);
        }

        public AnimationData(TextureAtlasSprite sprite, SpriteContents.AnimatedTexture animatedTexture) {
            this.sprite = sprite;
            this.animatedTexture = animatedTexture;
            atlas = Minecraft.getInstance().getModelManager().getAtlas(sprite.atlasLocation());
            data = animatedTexture.frames.stream()
                .map(frame -> IntIntPair.of(frame.index, frame.time))
                .toList();
            @SuppressWarnings("resource")
            var contents = sprite.contents();
            frames = IntArrayList.wrap(contents.getUniqueFrames().toArray());
        }

        public void upload(int index) {
            var x = sprite.getX();
            var y = sprite.getY();
            atlas.bind();
            animatedTexture.uploadFrame(x, y, index);
        }
    }

}
