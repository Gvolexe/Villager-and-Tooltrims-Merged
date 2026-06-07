package dev.gvol.villagertrims;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class ResourcePackService {
    private final VillagerToolTrimsPlugin plugin;
    private final PluginSettings.ResourcePacks settings;

    public ResourcePackService(VillagerToolTrimsPlugin plugin, PluginSettings.ResourcePacks settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void sendPacks(Player player) {
        if (!this.settings.enabled()) {
            return;
        }

        List<ResourcePackInfo> packs = new ArrayList<>();
        for (PluginSettings.ConfiguredResourcePack pack : this.settings.packs()) {
            try {
                packs.add(ResourcePackInfo.resourcePackInfo(pack.id(), URI.create(pack.url()), pack.sha1()));
            } catch (IllegalArgumentException exception) {
                this.plugin.getLogger().warning("Invalid resource pack config for '" + pack.name() + "': " + exception.getMessage());
            }
        }

        if (!packs.isEmpty()) {
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(packs)
                    .replace(false)
                    .build());
        }
    }
}
