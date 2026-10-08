package com.yungnickyoung.minecraft.yungsroads.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.GlobalSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadType;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import com.yungnickyoung.minecraft.yungsroads.world.config.ITunableSetting;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Exports road types edited on the tuning screen as datapack files, so they can be used in other worlds and modpacks.
 */
public final class RoadTypeExport {
    /** The name of the datapack in the world's datapacks folder that tuned road types are saved to. */
    public static final String DATAPACK_NAME = "yungsroads_tuned";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private RoadTypeExport() {
    }

    /**
     * Writes a road type to the world's tuned road type datapack, with a README describing the format and every setting.
     * The file replaces any saved before for the same id, and overrides the road type of that id in this world. Vanilla
     * enables new datapacks in a world's datapacks folder when the world loads, so the road type is used from the next
     * time it loads.
     */
    public static void writeWorldDatapack(MinecraftServer server, ResourceLocation id, RoadType roadType) throws IOException {
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DATAPACK_NAME);
        Path file = pack.resolve("data").resolve(id.getNamespace())
                .resolve(Registries.elementsDirPath(RoadTypes.REGISTRY_KEY))
                .resolve(id.getPath() + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson(roadType, server.registryAccess()), StandardCharsets.UTF_8);

        JsonObject packInfo = new JsonObject();
        packInfo.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        packInfo.addProperty("description", Language.getInstance().getOrDefault("yungsroads.docs.title"));
        JsonObject mcmeta = new JsonObject();
        mcmeta.add("pack", packInfo);
        Files.writeString(pack.resolve("pack.mcmeta"), GSON.toJson(mcmeta) + "\n", StandardCharsets.UTF_8);
        Files.writeString(pack.resolve("README.md"), readme(), StandardCharsets.UTF_8);
    }

    /** The road type as the contents of its datapack file. */
    public static String toJson(RoadType roadType, RegistryAccess registryAccess) {
        JsonElement json = RoadType.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registryAccess), roadType)
                .getOrThrow(error -> new IllegalStateException("Unable to encode road type: " + error));
        return GSON.toJson(json) + "\n";
    }

    /**
     * Describes the road type file format and every setting, from the same lang entries the tuning screen shows, so
     * the docs always match the settings.
     */
    static String readme() {
        StringBuilder readme = new StringBuilder();
        readme.append("# ").append(text("yungsroads.docs.title")).append("\n\n");
        readme.append(text("yungsroads.docs.intro")).append("\n\n");

        readme.append("## ").append(text("yungsroads.docs.choosing.title")).append("\n\n");
        for (String paragraph : text("yungsroads.help.road_types.text").split("\n")) {
            readme.append(paragraph).append("\n\n");
        }

        readme.append("## ").append(text("yungsroads.help.road_networks.title")).append("\n\n");
        for (String paragraph : text("yungsroads.help.road_networks.text").split("\n")) {
            readme.append(paragraph).append("\n\n");
        }

        readme.append("## ").append(text("yungsroads.docs.format.title")).append("\n\n");
        readme.append(text("yungsroads.docs.format.text")).append("\n\n");
        readme.append("""
                ```json
                {
                  "selection": {
                    "biomes": "#minecraft:is_mountain",
                    "priority": 10
                  },
                  "settings": {
                    "routing": { ... },
                    "shaping": { ... },
                    "blocks": { ... }
                  }
                }
                ```

                ```json
                {
                  "selection": { ... },
                  "variants": [
                    { "weight": 3, "settings": { ... } },
                    { "weight": 1, "settings": { ... } }
                  ]
                }
                ```

                """);

        readme.append("## ").append(text("yungsroads.docs.settings.title")).append("\n\n");
        readme.append(text("yungsroads.docs.settings.text")).append("\n\n");
        for (ITunableSetting.Group group : ITunableSetting.Group.values()) {
            String groupKey = group.name().toLowerCase(Locale.ROOT);
            readme.append("### `").append(groupKey).append("`\n\n");
            readme.append("| ").append(text("yungsroads.docs.column.setting"))
                    .append(" | ").append(text("yungsroads.docs.column.description"))
                    .append(" | ").append(text("yungsroads.docs.column.range"))
                    .append(" | ").append(text("yungsroads.docs.column.default")).append(" |\n");
            readme.append("| --- | --- | --- | --- |\n");
            for (RoadSetting setting : RoadSetting.values()) {
                if (setting.group() == group) {
                    readme.append(settingRow(setting));
                }
            }
            readme.append("\n");
        }

        readme.append("### `blocks`\n\n");
        readme.append("- `surfaces`: ").append(text("yungsroads.docs.blocks.surfaces")).append("\n");
        for (String field : new String[]{"target_blocks", "path_blockstates", "fill_blockstates",
                "road_size_radius", "road_size_variation"}) {
            readme.append("  - `").append(field).append("`: ").append(text("yungsroads.docs.blocks." + field)).append("\n");
        }
        for (String field : new String[]{"bridge_blockstates", "bridge_railing_blockstates", "tunnel_lining_blockstates"}) {
            readme.append("- `").append(field).append("`: ").append(text("yungsroads.docs.blocks." + field)).append("\n");
        }
        readme.append("\n");

        readme.append("## ").append(text("yungsroads.docs.global.title")).append("\n\n");
        readme.append(text("yungsroads.docs.global.text")).append("\n\n");
        readme.append("| ").append(text("yungsroads.docs.column.setting"))
                .append(" | ").append(text("yungsroads.docs.column.description"))
                .append(" | ").append(text("yungsroads.docs.column.range"))
                .append(" | ").append(text("yungsroads.docs.column.default")).append(" |\n");
        readme.append("| --- | --- | --- | --- |\n");
        for (GlobalSetting setting : GlobalSetting.values()) {
            readme.append("| ").append(text(setting.nameKey()))
                    .append(" | ").append(text(setting.descriptionKey()))
                    .append(" | ").append(range(setting))
                    .append(" | ").append(setting.format(setting.defaultValue())).append(" |\n");
        }
        return readme.toString();
    }

    private static String settingRow(RoadSetting setting) {
        String defaultValue = setting.isToggle()
                ? Boolean.toString(setting.defaultValue() != 0)
                : setting.format(setting.defaultValue());
        return "| `" + setting.key() + "` (" + text(setting.nameKey()) + ") | " + text(setting.descriptionKey())
                + " | " + range(setting) + " | `" + defaultValue + "` |\n";
    }

    private static String range(ITunableSetting setting) {
        return setting.isToggle()
                ? text("yungsroads.docs.toggle_range")
                : String.format(text("yungsroads.docs.range"), setting.format(setting.min()), setting.format(setting.max()));
    }

    private static String text(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
