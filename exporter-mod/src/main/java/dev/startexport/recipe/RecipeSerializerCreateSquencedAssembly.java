package dev.startexport.recipe;

import net.minecraft.world.item.crafting.Recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum RecipeSerializerCreateSquencedAssembly implements IRecipeSerializer {
    INSTANCE;

    @Override
    public @Nullable JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe.getSerializer() instanceof SequencedAssemblyRecipeSerializer sequencedAssemblySerializer) ||
            !(recipe instanceof SequencedAssemblyRecipe assemblyRecipe)) {
            return null;
        }

        var result = new JsonObject();
        sequencedAssemblySerializer.write(result, assemblyRecipe);
        return result;
    }

}
