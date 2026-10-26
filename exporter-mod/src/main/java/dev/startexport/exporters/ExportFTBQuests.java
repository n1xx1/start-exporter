package dev.startexport.exporters;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.startexport.utils.ExecutionUtils;
import dev.startexport.utils.FileUtils;
import dev.startexport.utils.JsonUtils;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ExportFTBQuests {

    public static CompletableFuture<Void> waitReady() {
        return ExecutionUtils.wait(ExportFTBQuests::isQuestbookLoaded);
    }

    public static CompletableFuture<Void> export() {
        return ExecutionUtils.submit(() -> {
            var mc = Minecraft.getInstance();
            var currentLanguage = mc.getLanguageManager().getSelected();
            var json = exportToJson(currentLanguage);
            FileUtils.saveExportedFile("quests.json", JsonUtils.toBytes(json));
            return null;
        });
    }

    private static boolean isQuestbookLoaded() {
        if (!ClientQuestFile.exists()) return false;
        var book = ClientQuestFile.INSTANCE;
        return !book.isLoading() && !book.getAllChapters().isEmpty();
    }

    private static JsonObject exportToJson(String language) {
        var book = ClientQuestFile.INSTANCE;

        var result = new JsonObject();
        result.add("data", questObjectJson(book).get("data"));

        var groups = book.getChapterGroups().stream()
            .collect(Collectors.toMap(v -> v.getCodeString(), v -> v));

        var exportedGroups = new JsonArray();
        groups.forEach((id, group) -> {
            var data = questObjectJson(group);
            data.addProperty("default", group.isDefaultGroup());
            var ids = new JsonArray();
            group.getChapters().forEach(chapter -> ids.add(chapter.getCodeString()));
            data.add("chapters", ids);
            exportedGroups.add(data);
        });
        result.add("chapterGroups", exportedGroups);

        var chapters = new JsonArray();
        for (var chapter : book.getAllChapters()) {
            var data = questObjectJson(chapter);
            data.addProperty("group", chapter.getGroup().getCodeString());

            var links = new JsonArray();
            chapter.getQuestLinks().forEach(link -> links.add(questObjectJson(link)));
            data.add("questLinks", links);

            var quests = new JsonArray();
            for (var quest : chapter.getQuests()) {
                var entry = questObjectJson(quest);
                entry.add("subtitle", JsonUtils.fromComponent(quest.getSubtitle()));

                var description = new JsonArray();
                quest.getDescription().forEach(line -> description.add(JsonUtils.fromComponent(line)));
                entry.add("description", description);

                var dependencies = new JsonArray();
                quest.streamDependencies().forEach(dependency -> dependencies.add(dependency.getCodeString()));
                entry.add("dependencies", dependencies);

                var tasks = new JsonArray();
                for (var task : quest.getTasks()) {
                    var typed = questObjectJson(task);
                    typed.addProperty("type", task.getType().getTypeId().toString());
                    tasks.add(typed);
                }
                entry.add("tasks", tasks);

                var rewards = new JsonArray();
                for (Reward reward : quest.getRewards()) {
                    var typed = questObjectJson(reward);
                    typed.addProperty("type", reward.getType().getTypeId().toString());
                    rewards.add(typed);
                }
                entry.add("rewards", rewards);

                quests.add(entry);
            }
            data.add("quests", quests);

            chapters.add(data);
        }
        result.add("chapters", chapters);

        var tables = new JsonArray();
        book.getRewardTables().forEach(table -> tables.add(questObjectJson(table)));
        result.add("rewardTables", tables);
        return result;
    }

    private static JsonObject questObjectJson(QuestObjectBase object) {
        CompoundTag tag = new CompoundTag();
        object.writeData(tag);
        JsonObject result = new JsonObject();
        result.addProperty("id", object.getCodeString());
        result.add("title", JsonUtils.fromComponent(object.getTitle()));
        result.add("data", JsonUtils.fromNbt(tag));
        return result;
    }
}
