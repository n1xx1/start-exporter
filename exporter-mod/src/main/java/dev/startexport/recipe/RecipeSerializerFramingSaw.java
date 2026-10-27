package dev.startexport.recipe;

import net.minecraft.world.item.crafting.Recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xfacthd.framedblocks.common.crafting.FramingSawRecipe;

public enum RecipeSerializerFramingSaw implements IRecipeSerializer {
    INSTANCE;

    @Override
    public @Nullable JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof FramingSawRecipe fsRecipe)) {
            return null;
        }

        var result = new JsonObject();
        result.addProperty("materialAmount", fsRecipe.getMaterialAmount());
        result.add("additives", fsRecipe.getAdditives().stream().map(additive -> {
            var jsonAdditive = new JsonObject();
            jsonAdditive.add("ingredient", additive.ingredient().toJson());
            jsonAdditive.addProperty("count", additive.count());
            return jsonAdditive;
        }).collect(JsonUtils.collectArray));
        result.add("result", JsonUtils.fromItemStack(fsRecipe.getResult()));
        result.addProperty("disabled", fsRecipe.isDisabled());
        return result;
    }

}
