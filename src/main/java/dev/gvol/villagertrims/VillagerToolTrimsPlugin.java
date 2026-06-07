package dev.gvol.villagertrims;

import dev.gvol.villagertrims.tooltrims.ToolTrimsInstaller;
import dev.gvol.villagertrims.villager.VillagerBucketListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class VillagerToolTrimsPlugin extends JavaPlugin implements Listener, TabExecutor {
    private PluginSettings settings;
    private ResourcePackService resourcePackService;
    private ToolTrimsInstaller toolTrimsInstaller;
    private VillagerBucketListener villagerBucketListener;

    @Override
    public void onEnable() {
        loadSettings();

        this.villagerBucketListener = new VillagerBucketListener(this);
        this.villagerBucketListener.reload();

        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(this.villagerBucketListener, this);

        if (this.getCommand("villagertrims") != null) {
            this.getCommand("villagertrims").setExecutor(this);
            this.getCommand("villagertrims").setTabCompleter(this);
        }

        installToolTrimsDatapack(false);
    }

    @Override
    public void onDisable() {
        if (this.villagerBucketListener != null) {
            this.villagerBucketListener.shutdown();
        }
    }

    public void loadSettings() {
        saveDefaultConfig();
        reloadConfig();
        this.settings = PluginSettings.fromConfig(getConfig());
        this.resourcePackService = new ResourcePackService(this, this.settings.resourcePacks());
        this.toolTrimsInstaller = new ToolTrimsInstaller(this, this.settings.toolTrims());
    }

    public PluginSettings settings() {
        return this.settings;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                this.resourcePackService.sendPacks(player);
            }
        }, 20L);
    }

    private void installToolTrimsDatapack(boolean force) {
        ToolTrimsInstaller.InstallResult result = this.toolTrimsInstaller.ensureInstalled(force);
        switch (result) {
            case INSTALLED -> {
                getLogger().info("Installed Tool Trims datapack.");
                if (this.settings.toolTrims().reloadDataAfterInstall()) {
                    Bukkit.reloadData();
                    getLogger().info("Reloaded server data after Tool Trims datapack installation.");
                }
            }
            case ALREADY_CURRENT -> getLogger().info("Tool Trims datapack is already installed.");
            case DISABLED -> getLogger().info("Tool Trims datapack auto-install is disabled.");
            case FAILED -> getLogger().warning("Tool Trims datapack installation failed. Check the log above for details.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("VillagerToolTrims " + getPluginMeta().getVersion(), NamedTextColor.GREEN));
            sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " datapack", NamedTextColor.GRAY));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            if (this.villagerBucketListener != null) {
                this.villagerBucketListener.reload();
            }
            sender.sendMessage(Component.text("VillagerToolTrims config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("datapack")) {
            installToolTrimsDatapack(true);
            sender.sendMessage(Component.text("Tool Trims datapack install check completed.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();
        for (String option : List.of("reload", "datapack")) {
            if (option.startsWith(prefix)) {
                completions.add(option);
            }
        }
        return completions;
    }
}
