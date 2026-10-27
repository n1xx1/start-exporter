package dev.startexport.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

public enum RecipeSerializerGenericShaped implements IRecipeSerializer {
    INSTANCE;

    public JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof ShapedRecipe shapedRecipe)) {
            return null;
        }
        var result = new JsonObject();
        result.addProperty("width", shapedRecipe.getWidth());
        result.addProperty("height", shapedRecipe.getHeight());
        result.add("ingredients", shapedRecipe.getIngredients().stream().map(Ingredient::toJson).collect(JsonUtils.collectArray));
        result.add("result", JsonUtils.fromItemStack(shapedRecipe.getResultItem(RegistryAccess.EMPTY)));
        return result;
    }

}
