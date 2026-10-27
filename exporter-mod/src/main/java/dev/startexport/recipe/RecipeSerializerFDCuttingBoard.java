package dev.startexport.recipe;

import net.minecraft.world.item.crafting.Recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.ingredient.ChanceResult;

public enum RecipeSerializerFDCuttingBoard implements IRecipeSerializer {
    INSTANCE;

    @Override
    public @Nullable JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof CuttingBoardRecipe cbRecipe)) {
            return null;
        }

        var result = new JsonObject();
        result.addProperty("group", cbRecipe.getGroup());
        result.add("input", cbRecipe.getIngredients().getFirst().toJson());
        result.add("tool", cbRecipe.getTool().toJson());
        result.add("results", cbRecipe.getRollableResults().stream()
            .map(ChanceResult::serialize).collect(JsonUtils.collectArray));
        return result;
    }

}
