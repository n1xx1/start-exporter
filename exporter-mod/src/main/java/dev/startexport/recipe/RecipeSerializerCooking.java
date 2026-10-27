package dev.startexport.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

public enum RecipeSerializerCooking implements IRecipeSerializer {
    INSTANCE;

    private boolean isCookingRecipeSerializer(RecipeSerializer<?> serializer) {
        return serializer == RecipeSerializer.SMELTING_RECIPE ||
            serializer == RecipeSerializer.BLASTING_RECIPE ||
            serializer == RecipeSerializer.SMOKING_RECIPE ||
            serializer == RecipeSerializer.CAMPFIRE_COOKING_RECIPE;
    }

    public JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!isCookingRecipeSerializer(recipe.getSerializer()) || !(recipe instanceof AbstractCookingRecipe cookingRecipe)) {
            return null;
        }
        var result = new JsonObject();
        result.addProperty("experience", cookingRecipe.getExperience());
        result.addProperty("cookingTime", cookingRecipe.getCookingTime());
        result.add("ingredient", cookingRecipe.getIngredients().getFirst().toJson());
        result.add("result", JsonUtils.fromItemStack(cookingRecipe.getResultItem(RegistryAccess.EMPTY)));
        return result;
    }

}
