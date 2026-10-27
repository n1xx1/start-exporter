package dev.startexport.recipe;

import net.minecraft.world.item.crafting.Recipe;

import appeng.recipes.entropy.EntropyRecipe;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.startexport.utils.JsonUtils;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public enum RecipeSerializerAEEntropy implements IRecipeSerializer {
    INSTANCE;

    @Override
    public @Nullable JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe instanceof EntropyRecipe aeRecipe)) {
            return null;
        }

        var result = new JsonObject();
        result.addProperty("mode", aeRecipe.getMode().name().toLowerCase());
        if (aeRecipe.getInputBlock() != null) {
            result.addProperty("inputBlock", Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(aeRecipe.getInputBlock())).toString());
        }
        if (aeRecipe.getInputFluid() != null) {
            result.addProperty("inputFluid", Objects.requireNonNull(ForgeRegistries.FLUIDS.getKey(aeRecipe.getInputFluid())).toString());
        }
        if (aeRecipe.getOutputBlock() != null) {
            result.addProperty("outputBlock", Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(aeRecipe.getOutputBlock())).toString());
            result.addProperty("outputBlockKeep", aeRecipe.getOutputBlockKeep());
        }
        if (aeRecipe.getOutputFluid() != null) {
            result.addProperty("outputFluid", Objects.requireNonNull(ForgeRegistries.FLUIDS.getKey(aeRecipe.getOutputFluid())).toString());
            result.addProperty("outputFluidKeep", aeRecipe.getOutputFluidKeep());
        }
        result.add("drops", aeRecipe.getDrops().stream().map(JsonUtils::fromItemStack).collect(JsonUtils.collectArray));
        return result;
    }

}
