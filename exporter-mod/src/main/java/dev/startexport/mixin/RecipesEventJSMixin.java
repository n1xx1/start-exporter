package dev.startexport.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipesEventJS;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

import dev.startexport.exporters.ExportEMI;
import dev.startexport.utils.ExecutionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.recipe.RecipesEventJS", remap = false)
public abstract class RecipesEventJSMixin {

    @Inject(method = "post", at = @At("RETURN"))
    private void startexporter$afterRecipes(
        RecipeManager recipeManager,
        Map<ResourceLocation, JsonElement> datapackRecipeMap,
        CallbackInfo ci
    ) {
        var event = (RecipesEventJS)(Object)this;

        // force id creation
        var allRecipes = Stream.concat(event.originalRecipes.values().stream(), event.addedRecipes.stream())
            .filter(recipe -> !recipe.removed && recipe.json != null)
            .toList();

        allRecipes.forEach(RecipeJS::getOrCreateId);

        ExecutionUtils.submit(() -> {
            var captured = new HashMap<ResourceLocation, JsonObject>();
            allRecipes.forEach(recipe -> {
                if (recipeManager.byKey(recipe.id).isPresent()) {
                    captured.put(recipe.id, recipe.json.deepCopy());
                }
            });
            ExportEMI.loadedRecipes = captured;
            ExportEMI.recipesLoadedFuture.complete(null);
            return null;
        });
    }
}
