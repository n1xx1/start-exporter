package dev.startexport.recipe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

public enum RecipeSerializerSmithingTrim implements IRecipeSerializer {
    INSTANCE;

    public JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof SmithingTrimRecipe tRecipe)) {
            return null;
        }

        var serializer = (SmithingTrimRecipe.Serializer) tRecipe.getSerializer();

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, tRecipe);
            buffer.readerIndex(0);

            var result = new JsonObject();
            result.add("template", Ingredient.fromNetwork(buffer).toJson());
            result.add("base", Ingredient.fromNetwork(buffer).toJson());
            result.add("addition", Ingredient.fromNetwork(buffer).toJson());
            return result;
        } finally {
            buffer.release();
        }
    }

}
