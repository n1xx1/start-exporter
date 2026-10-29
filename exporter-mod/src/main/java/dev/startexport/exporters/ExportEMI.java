package dev.startexport.exporters;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.screen.WidgetGroup;
import dev.startexport.recipe.*;
import dev.startexport.utils.FutureUtils;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.startexport.StarTExporter;
import dev.startexport.utils.FileUtils;
import dev.startexport.utils.JsonUtils;
import dev.startexport.utils.ExecutionUtils;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ExportEMI {

    public static final AtomicBoolean isIndexingDone = new AtomicBoolean(false);
    public static final LinkedBlockingQueue<ItemToExport> itemsExportQueue = new LinkedBlockingQueue<>();
    public static final LinkedBlockingQueue<FluidToExport> fluidsExportQueue = new LinkedBlockingQueue<>();

    public static final AtomicBoolean isRecipeGatheringDone = new AtomicBoolean(false);
    public static final LinkedBlockingQueue<RecipeToExport> recipeExportQueue = new LinkedBlockingQueue<>();

    public static CompletableFuture<Void> waitReady() {
        return ExecutionUtils.wait(EmiReloadManager::isLoaded);
    }

    public static CompletableFuture<Void> export() {
        return FutureUtils.allOf(
            exportRecipes(),
            exportIndex()
        );
    }

    private static CompletableFuture<Void> exportRecipes() {
        return ExecutionUtils.submit(() -> {
                var manager = EmiApi.getRecipeManager();
                return manager.getCategories().stream().map(ExportEMI::exportRecipeCategory).toArray(CompletableFuture[]::new);
            })
            .thenCompose(CompletableFuture::allOf)
            .thenCompose((_void) -> {
                isRecipeGatheringDone.set(true);
                return ExecutionUtils.wait(ExportEMI::isRecipeExportingDone);
            });
    }

    private static ResourceLocation getEmiRecipeId(EmiRecipeCategory category, int index, EmiRecipe emiRecipe) {
        var id = emiRecipe.getId();
        if (id == null) {
            var recipe = emiRecipe.getBackingRecipe();
            if (recipe != null) {
                id = recipe.getId();
            }
        }
        if (id == null) {
            id = ResourceLocation.parse(category.id.getNamespace() + ":/" + category.id.getPath() + "missingid-" + index);
        }
        return id;
    }

    private static @Nullable Recipe<?> getEmiBackingRecipe(EmiRecipe emiRecipe) {
        var recipe = emiRecipe.getBackingRecipe();
        var level = Minecraft.getInstance().level;
        if (recipe == null && level != null && emiRecipe.getId() != null) {
            recipe = level.getRecipeManager().byKey(emiRecipe.getId()).orElse(null);
        }
        return recipe;
    }

    private static CompletableFuture<Void> exportRecipeCategory(EmiRecipeCategory category) {
        var manager = EmiApi.getRecipeManager();
        return ExecutionUtils.submit(() -> {
            var recipes = new JsonArray();
            // var recipeCategoryPath = FileUtils.resourceLocationToPath(category.getId());
            var categoryRecipes = manager.getRecipes(category);
            for (var i = 0; i < categoryRecipes.size(); i++) {
                var emiRecipe = categoryRecipes.get(i);
                var id = getEmiRecipeId(category, i, emiRecipe);

                var result = new JsonObject();

                var recipe = getEmiBackingRecipe(emiRecipe);
                if (recipe != null) {
                    result.addProperty("type", recipe.getType().toString());
                }

                result.addProperty("id", id.toString());

                var widget = new WidgetGroup(null, 0, 0, emiRecipe.getDisplayWidth(), emiRecipe.getDisplayHeight());
                emiRecipe.addWidgets(widget);

                result.add("emi", encodeEmiRecipe(emiRecipe, widget));

                var recipeIdPath = FileUtils.resourceLocationToPath(id);
                var recipePath = "recipe-images/" + recipeIdPath + ".png";
                recipeExportQueue.put(new RecipeToExport(recipePath, emiRecipe, widget));
                result.addProperty("image", recipePath);

                var raw = encodeRecipe(emiRecipe);
                if (raw != null) {
                    result.add("raw", raw);
                }

                recipes.add(result);
            }

            var exportPath = FileUtils.resourceLocationToPath(category.getId());
            FileUtils.saveExportedFile("recipes/" + exportPath + ".json", JsonUtils.toBytes(recipes));
            StarTExporter.LOG.info("Collected {} recipes for {}", recipes.size(), category.getId());
            return null;
        });
    }

    private static JsonElement encodeEmiRecipe(EmiRecipe emiRecipe, WidgetGroup widget) {
        var result = new JsonObject();

        result.addProperty("width", (widget.width + ExportRecipeImages.RECIPE_BORDER) * ExportRecipeImages.RECIPE_SCALE);
        result.addProperty("height", (widget.height + ExportRecipeImages.RECIPE_BORDER) * ExportRecipeImages.RECIPE_SCALE);

        result.add("slots", widget.widgets.stream()
            .filter(SlotWidget.class::isInstance)
            .map(SlotWidget.class::cast)
            .map(w -> {
                var bounds = w.getBounds();
                var slot = new JsonObject();
                slot.addProperty("x", (bounds.x() + ExportRecipeImages.RECIPE_BORDER) * ExportRecipeImages.RECIPE_SCALE);
                slot.addProperty("y", (bounds.y() + ExportRecipeImages.RECIPE_BORDER) * ExportRecipeImages.RECIPE_SCALE);
                slot.addProperty("width", bounds.width() * ExportRecipeImages.RECIPE_SCALE);
                slot.addProperty("height", bounds.height() * ExportRecipeImages.RECIPE_SCALE);
                slot.add("ingredient", emiIngredientToJson(w.getStack()));
                return slot;
            }).collect(JsonUtils.collectArray));

        result.add("inputs", emiRecipe.getInputs().stream()
            .map(ExportEMI::emiIngredientToJson)
            .collect(JsonUtils.collectArray));

        result.add("catalysts", emiRecipe.getCatalysts().stream()
            .map(ExportEMI::emiIngredientToJson)
            .collect(JsonUtils.collectArray));

        result.add("outputs", emiRecipe.getOutputs().stream()
            .map(ExportEMI::emiIngredientToJson)
            .collect(JsonUtils.collectArray));

        return result;
    }

    private static final List<IRecipeSerializer> recipeSerializers = Stream.<IRecipeSerializer>of(
        ModList.get().isLoaded("gtceu") ? RecipeSerializerGregtech.INSTANCE : null,
        ModList.get().isLoaded("ae2") ? RecipeSerializerAEEntropy.INSTANCE : null,
        ModList.get().isLoaded("ae2") ? RecipeSerializerAETransform.INSTANCE : null,
        ModList.get().isLoaded("create") ? RecipeSerializerCreateProcessing.INSTANCE : null,
        ModList.get().isLoaded("create") ? RecipeSerializerCreateSquencedAssembly.INSTANCE : null,
        ModList.get().isLoaded("farmersdelight") ? RecipeSerializerFDCuttingBoard.INSTANCE : null,
        ModList.get().isLoaded("farmersdelight") ? RecipeSerializerFDCookingPot.INSTANCE : null,
        ModList.get().isLoaded("framedblocks") ? RecipeSerializerFramingSaw.INSTANCE : null,
        RecipeSerializerCooking.INSTANCE,
        RecipeSerializerSmithingTransform.INSTANCE,
        RecipeSerializerSmithingTrim.INSTANCE,
        RecipeSerializerGenericShaped.INSTANCE,
        RecipeSerializerGenericShapeless.INSTANCE,
        RecipeSerializerGenericSingleItem.INSTANCE
    ).filter(Objects::nonNull).toList();

    @Nullable
    private static JsonElement encodeRecipe(EmiRecipe emiRecipe) {
        var recipe = getEmiBackingRecipe(emiRecipe);
        if (recipe == null) {
            return null;
        }

        for (var serializer : recipeSerializers) {
            var result = serializer.serialize(recipe);
            if (result != null) {
                return result;
            }
        }

        StarTExporter.LOG.info("Failed to serialize recipe {} (emi: {}, recipe: {})", emiRecipe.getId(), emiRecipe, recipe);

        return null;
    }

    public static boolean isRecipeGatheringDone() {
        return isRecipeGatheringDone.get();
    }

    public static boolean isRecipeExportingDone() {
        return isRecipeGatheringDone() && recipeExportQueue.isEmpty();
    }

    private static boolean shouldSkipStack(EmiStack stack) {
        if (stack.isEmpty()) return true;
        return (stack.getId().toString().equals("cb_microblock:microblock"));
    }

    private static CompletableFuture<Void> exportIndex() {
        return ExecutionUtils.submit(() -> {
            var items = new JsonArray();

            var count = 0;
            for (var stack : EmiApi.getIndexStacks()) {
                if (shouldSkipStack(stack)) continue;

                var entry = emiStackToJson(stack, 0).getAsJsonObject();
                entry.add("name", JsonUtils.fromComponent(stack.getName()));

                var isFluid = stack.getKey() instanceof Fluid;
                entry.addProperty("fluid", isFluid);

                var suffix = stack.hasNbt() ? "_" + JsonUtils.hashNbt(stack.getNbt()) : "";
                var imageBasePath = FileUtils.resourceLocationToPath(stack.getId()) + suffix + ".png";

                if (isFluid) {
                    var fluidStack = new FluidStack((Fluid) stack.getKey(), 1000);
                    if (stack.getNbt() != null) {
                        fluidStack.setTag(stack.getNbt().copy());
                    }

                    var animationData = ExportItemImages.getAnimationDataForFluid(fluidStack);
                    if (animationData != null) {
                        entry.addProperty("animationFrames", animationData.frames.size());
                        entry.addProperty("animationInterpolated", animationData.interpolated);
                    }

                    var imagePath = "fluids/" + imageBasePath;
                    entry.addProperty("image", imagePath);
                    fluidsExportQueue.put(new FluidToExport(imagePath, fluidStack, animationData));
                } else {
                    var itemStack = stack.getItemStack();
                    var tooltip = new JsonArray();
                    itemStack.getTooltipLines(null, TooltipFlag.ADVANCED).forEach(line -> tooltip.add(JsonUtils.fromComponent(line)));
                    entry.add("tooltip", tooltip);

                    entry.add("description", JsonUtils.fromComponent(itemStack.getItem().getDescription()));

                    var animationData = ExportItemImages.getAnimationDataForItem(itemStack);
                    if (animationData != null) {
                        entry.addProperty("animationFrames", animationData.frames.size());
                    }

                    var imagePath = "items/" + imageBasePath;
                    entry.addProperty("image", imagePath);
                    itemsExportQueue.put(new ItemToExport(imagePath, itemStack, animationData));
                }

                items.add(entry);
                count++;

                if (count % 500 == 0) {
                    StarTExporter.LOG.info("Loaded {} item/fluid entries", count);
                }
            }
            if (count % 500 != 0) {
                StarTExporter.LOG.info("Loaded {} item/fluid entries", count);
            }

            FileUtils.saveExportedFile("all_items.json", JsonUtils.toBytes(items));
            return null;
        }).thenCompose((_void) -> {
            isIndexingDone.set(true);
            return ExecutionUtils.wait(() -> isItemExportingDone() && isFluidExportingDone());
        });
    }

    public static boolean isIndexingDone() {
        return isIndexingDone.get();
    }

    public static boolean isItemExportingDone() {
        return isIndexingDone() && itemsExportQueue.isEmpty();
    }

    public static boolean isFluidExportingDone() {
        return isIndexingDone() && fluidsExportQueue.isEmpty();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static JsonElement emiIngredientToJson(EmiIngredient value) {
        if (value.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof EmiStack stack) {
            return emiStackToJson(stack, 0);
        }

        var result = new JsonObject();
        if (value instanceof TagEmiIngredient tag) {
            result.addProperty("id", "#" + tag.key.location());
        } else {
            var alternatives = new JsonArray();
            value.getEmiStacks().forEach(stack -> alternatives.add(emiStackToJson(stack, 0)));
            result.add("value", alternatives);
        }
        if (value.getAmount() != 1) {
            result.addProperty("amount", value.getAmount());
        }
        if (value.getChance() != 1) {
            result.addProperty("chance", value.getChance());
        }
        return result;
    }

    private static JsonElement emiStackToJson(EmiStack stack, int depth) {
        if (stack.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        if (depth > 16) {
            throw new IllegalStateException("Cyclic or excessively nested recipe remainder: " + stack.getId());
        }

        var result = new JsonObject();
        result.addProperty("id", stack.getId().toString());

        if (stack.getAmount() > 1) {
            result.addProperty("amount", stack.getAmount());
        }
        if (stack.getChance() != 1) {
            result.addProperty("chance", stack.getChance());
        }
        if (stack.hasNbt()) {
            result.add("nbt", JsonUtils.fromNbt(stack.getNbt()));
        }
        var remainder = stack.getRemainder();
        if (remainder != null && !remainder.isEmpty()) {
            if (remainder.isEqual(stack)) {
                result.addProperty("remainder", "self");
            } else {
                result.add("remainder", emiStackToJson(remainder, depth + 1));
            }
        }
        return result;
    }

    public record ItemToExport(String path, ItemStack stack, @Nullable ExportItemImages.AnimationData animationData) {
    }

    public record FluidToExport(String path, FluidStack stack, @Nullable ExportItemImages.AnimationData animationData) {
    }

    public record RecipeToExport(String path, EmiRecipe recipe, WidgetGroup emiWidgetGroup) {
    }
}
