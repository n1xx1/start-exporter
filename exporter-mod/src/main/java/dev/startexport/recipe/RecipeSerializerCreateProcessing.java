package dev.startexport.recipe;

import net.minecraft.world.item.crafting.Recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum RecipeSerializerCreateProcessing implements IRecipeSerializer {
    INSTANCE;

    @Override
    public @Nullable JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe.getSerializer() instanceof ProcessingRecipeSerializer<?>) ||
            !(recipe instanceof ProcessingRecipe<?> processingRecipe)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        var processingRecipeSerializer = ((ProcessingRecipeSerializer<ProcessingRecipe<?>>) recipe.getSerializer());
        var result = new JsonObject();
        processingRecipeSerializer.write(result, processingRecipe);
        return result;
    }

}
