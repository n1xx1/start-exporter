package dev.startexport.recipe;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import appeng.recipes.entropy.EntropyRecipe;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipe;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public enum RecipeSerializerAETransform implements IRecipeSerializer {
    INSTANCE;

    @Override
    public @Nullable JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof TransformRecipe aeRecipe)) {
            return null;
        }

        var result = new JsonObject();
        result.add("output", JsonUtils.fromItemStack(aeRecipe.getResultItem()));
        result.add("ingredients", aeRecipe.getIngredients().stream().map(Ingredient::toJson).collect(JsonUtils.collectArray));
        result.add("circumstance", aeRecipe.circumstance.toJson());
        return result;
    }

}
