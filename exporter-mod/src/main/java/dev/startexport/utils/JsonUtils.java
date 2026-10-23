package dev.startexport.utils;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
        var prevStyle = new Style[] { null };
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

    public static String hashNbt(CompoundTag tag) {
        return HexFormat.of().formatHex(MD5.digest(toBytes(fromNbt(tag))));
    }

    private static final MessageDigest MD5;

    static {
        try {
            MD5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
