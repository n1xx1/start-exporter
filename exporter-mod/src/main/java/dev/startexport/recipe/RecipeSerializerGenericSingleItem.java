package dev.startexport.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

public enum RecipeSerializerGenericSingleItem implements IRecipeSerializer {
    INSTANCE;

    public JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof SingleItemRecipe siRecipe) || !(recipe.getSerializer() instanceof SingleItemRecipe.Serializer<?> serializer)) {
            return null;
        }
        var result = new JsonObject();
        result.add("ingredient", siRecipe.getIngredients().getFirst().toJson());
        result.add("result", JsonUtils.fromItemStack(siRecipe.getResultItem(RegistryAccess.EMPTY)));
        return result;
    }

}
