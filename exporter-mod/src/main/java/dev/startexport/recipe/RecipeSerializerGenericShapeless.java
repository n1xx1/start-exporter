package dev.startexport.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

public enum RecipeSerializerGenericShapeless implements IRecipeSerializer {
    INSTANCE;

    public JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof ShapelessRecipe shapelessRecipe)) {
            return null;
        }
        var result = new JsonObject();
        result.add("ingredients", shapelessRecipe.getIngredients().stream().map(Ingredient::toJson).collect(JsonUtils.collectArray));
        result.add("result", JsonUtils.fromItemStack(shapelessRecipe.getResultItem(RegistryAccess.EMPTY)));
        return result;
    }

}
