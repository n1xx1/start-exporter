package dev.startexport.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Recipe;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;

public enum RecipeSerializerGregtech implements IRecipeSerializer {
    INSTANCE;

    public JsonElement serialize(@NotNull Recipe<?> recipe) {
        if (!(recipe.getSerializer() instanceof GTRecipeSerializer) || !(recipe instanceof GTRecipe gtRecipe)) {
            return null;
        }

        var ops = RegistryOps.create(JsonOps.INSTANCE, GTRegistries.builtinRegistry());
        return GTRecipeSerializer.CODEC.encodeStart(ops, gtRecipe).result().orElse(null);
    }

}
