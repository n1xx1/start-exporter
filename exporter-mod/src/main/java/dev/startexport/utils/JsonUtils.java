package dev.startexport.utils;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

public final class JsonUtils {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static byte[] toBytes(JsonElement value) {
        return GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    public static JsonElement fromComponent(Component value) {
        var legacyColorMap = Stream.of(ChatFormatting.values()).filter(ChatFormatting::isColor)
            .collect(ImmutableMap.toImmutableMap(TextColor::fromLegacyFormat, Function.identity()));

        var builder = new StringBuilder();
        var prevStyle = new Style[]{null};
        value.visit((style, text) -> {
            if (!style.equals(prevStyle[0])) {
                if (prevStyle[0] != null) builder.append(ChatFormatting.RESET);
                if (style.isBold()) builder.append(ChatFormatting.BOLD);
                if (style.isItalic()) builder.append(ChatFormatting.ITALIC);
                if (style.isUnderlined()) builder.append(ChatFormatting.UNDERLINE);
                if (style.isStrikethrough()) builder.append(ChatFormatting.STRIKETHROUGH);
                if (style.isObfuscated()) builder.append(ChatFormatting.OBFUSCATED);
                if (style.getColor() != null) {
                    var chatFormatting = legacyColorMap.get(style.getColor());
                    if (chatFormatting != null) builder.append(chatFormatting);
                }
                prevStyle[0] = style;
            }
            builder.append(text);
            return Optional.empty();
        }, Style.EMPTY);

        return new JsonPrimitive(builder.toString());
    }

    public static JsonElement fromNbt(Tag tag) {
        switch (tag) {
            case null -> {
                return JsonNull.INSTANCE;
            }
            case EndTag _t -> {
                return JsonNull.INSTANCE;
            }
            case CompoundTag compound -> {
                JsonObject object = new JsonObject();
                compound.getAllKeys().stream().sorted().forEach(key -> object.add(key, fromNbt(compound.get(key))));
                return object;
            }
            case CollectionTag<?> collection -> {
                JsonArray array = new JsonArray();
                collection.forEach(element -> array.add(fromNbt(element)));
                return array;
            }
            case LongTag number -> {
                return new JsonPrimitive(Long.toString(number.getAsLong()));
            }
            case NumericTag number -> {
                return new JsonPrimitive(number.getAsNumber());
            }
            default -> {
            }
        }
        return new JsonPrimitive(tag.getAsString());
    }

    public static JsonElement fromItemStack(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).result().orElseThrow();
    }

    public static String hashNbt(CompoundTag tag) {
        return HexFormat.of().formatHex(MD5.digest(toBytes(fromNbt(tag))));
    }

    private static final MessageDigest MD5;

    public static Collector<JsonElement, JsonArray, JsonArray> collectArray = new JsonArrayCollector();

    private static class JsonArrayCollector implements Collector<JsonElement, JsonArray, JsonArray> {
        private static final Set<Collector.Characteristics> characteristics = Set.of(Characteristics.IDENTITY_FINISH);

        @Override
        public Supplier<JsonArray> supplier() {
            return JsonArray::new;
        }

        @Override
        public BiConsumer<JsonArray, JsonElement> accumulator() {
            return JsonArray::add;
        }

        @Override
        public BinaryOperator<JsonArray> combiner() {
            return (a, b) -> {
                var result = new JsonArray(a.size() + b.size());
                result.addAll(a);
                result.addAll(b);
                return result;
            };
        }

        @Override
        public Function<JsonArray, JsonArray> finisher() {
            return Function.identity();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return characteristics;
        }
    }

    static {
        try {
            MD5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
