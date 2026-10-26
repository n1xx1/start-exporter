package dev.startexport.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import dev.startexport.StarTExporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileUtils {

    public static String resourceLocationToPath(ResourceLocation rl) {
        return rl.getNamespace() + "/" + rl.getPath().replace('/', '~');
    }

    public static Path resolveGameDirPath(String relative) {
        var mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve(relative).toAbsolutePath().normalize();
    }

    public static void saveExportedFile(String relative, byte[] bytes) throws IOException {
        var root = StarTExporter.INSTANCE.exportDirectory;
        var target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Invalid export path: " + relative);
        }

        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

}
