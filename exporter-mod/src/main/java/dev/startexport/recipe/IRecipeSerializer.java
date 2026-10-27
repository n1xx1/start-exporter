package dev.startexport.recipe;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public interface IRecipeSerializer {

    @Nullable JsonElement serialize(@NotNull Recipe<?> recipe);

}
