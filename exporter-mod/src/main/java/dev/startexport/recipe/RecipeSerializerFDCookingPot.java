package dev.startexport.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.ingredient.ChanceResult;

public enum RecipeSerializerFDCookingPot implements IRecipeSerializer {
    INSTANCE;

    @Override
    public @Nullable JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof CookingPotRecipe cpRecipe)) {
            return null;
        }

        var result = new JsonObject();
        result.addProperty("group", cpRecipe.getGroup());
        if (cpRecipe.getRecipeBookTab() != null) {
            result.addProperty("tab", cpRecipe.getRecipeBookTab().toString());
        }
        result.add("inputs", cpRecipe.getIngredients().stream().map(Ingredient::toJson).collect(JsonUtils.collectArray));
        result.add("output", JsonUtils.fromItemStack(cpRecipe.getResultItem(RegistryAccess.EMPTY)));
        result.add("container", JsonUtils.fromItemStack(cpRecipe.getOutputContainer()));
        result.addProperty("experience", cpRecipe.getExperience());
        result.addProperty("cookTime", cpRecipe.getCookTime());
        return result;
    }

}
