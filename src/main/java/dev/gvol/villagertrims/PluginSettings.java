package dev.gvol.villagertrims;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PluginSettings(
        VillagerBuckets villagerBuckets,
        ToolTrims toolTrims,
        ResourcePacks resourcePacks
) {
    public static PluginSettings fromConfig(FileConfiguration config) {
        return new PluginSettings(
                new VillagerBuckets(
                        config.getBoolean("villager-buckets.enabled", true),
                        config.getBoolean("villager-buckets.harm-reputation", false),
                        config.getBoolean("villager-buckets.console-logging", false),
                        config.getBoolean("villager-buckets.file-logging", false)
                ),
                new ToolTrims(
                        config.getBoolean("tool-trims.auto-install-datapack", true),
                        config.getBoolean("tool-trims.reload-data-after-install", true),
                        config.getString("tool-trims.datapack-filename", "tool-trims-v3.0.1-beta-for-1.21.9+.zip"),
                        config.getString("tool-trims.datapack-url", ""),
                        config.getString("tool-trims.datapack-sha1", "")
                ),
                readResourcePacks(config)
        );
    }

    private static ResourcePacks readResourcePacks(FileConfiguration config) {
        boolean enabled = config.getBoolean("resource-packs.enabled", true);
        List<ConfiguredResourcePack> packs = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("resource-packs.packs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (!section.getBoolean(key + ".enabled", true)) {
                    continue;
                }
                String id = section.getString(key + ".id", "");
                String url = section.getString(key + ".url", "");
                String sha1 = section.getString(key + ".sha1", "");
                if (id.isBlank() || url.isBlank() || sha1.isBlank()) {
                    continue;
                }
                try {
                    packs.add(new ConfiguredResourcePack(key, UUID.fromString(id), url, sha1));
                } catch (IllegalArgumentException ignored) {
                    // Invalid resource pack entries are reported when sending packs.
                }
            }
        }
        return new ResourcePacks(enabled, packs);
    }

    public record VillagerBuckets(
            boolean enabled,
            boolean harmReputation,
            boolean consoleLogging,
            boolean fileLogging
    ) {
    }

    public record ToolTrims(
            boolean autoInstallDatapack,
            boolean reloadDataAfterInstall,
            String datapackFilename,
            String datapackUrl,
            String datapackSha1
    ) {
    }

    public record ResourcePacks(
            boolean enabled,
            List<ConfiguredResourcePack> packs
    ) {
    }

    public record ConfiguredResourcePack(
            String name,
            UUID id,
            String url,
            String sha1
    ) {
    }
}
