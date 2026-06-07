package dev.gvol.villagertrims.villager;

import com.destroystokyo.paper.entity.villager.Reputation;
import com.destroystokyo.paper.entity.villager.ReputationType;
import dev.gvol.villagertrims.PluginSettings;
import dev.gvol.villagertrims.VillagerToolTrimsPlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public final class VillagerBucketListener implements Listener {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> DEFAULT_BUCKET_NAMES = Set.of(
            "Villager In A Bucket",
            "Zombie Villager In A Bucket",
            "Wandering Trader In A Bucket"
    );

    private final VillagerToolTrimsPlugin plugin;
    private final NamespacedKey villagerDataKey;
    private FileWriter logFileWriter;

    public VillagerBucketListener(VillagerToolTrimsPlugin plugin) {
        this.plugin = plugin;
        this.villagerDataKey = new NamespacedKey(plugin, "villager_data");
    }

    public void reload() {
        closeLogWriter();
        if (!settings().fileLogging()) {
            return;
        }

        try {
            File logFile = new File(this.plugin.getDataFolder(), "villager-actions.log");
            this.logFileWriter = new FileWriter(logFile, true);
        } catch (IOException exception) {
            this.plugin.getLogger().severe("Unable to create villager action log file: " + exception.getMessage());
        }
    }

    public void shutdown() {
        closeLogWriter();
    }

    @EventHandler
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!settings().enabled()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemStack = player.getInventory().getItem(event.getHand());
        Entity clicked = event.getRightClicked();

        if (itemStack.getType() != Material.BUCKET) {
            return;
        }

        if (isVillagerBucket(itemStack)) {
            event.setCancelled(true);
            return;
        }

        if (!canPickup(player, clicked)) {
            return;
        }

        Location location = clicked.getLocation();
        if (itemStack.getAmount() > 1 || player.getGameMode() == GameMode.CREATIVE) {
            ItemStack newStack = new ItemStack(Material.BUCKET);
            createVillagerBucket(newStack, clicked, player);
            if (player.getGameMode() != GameMode.CREATIVE) {
                itemStack.setAmount(itemStack.getAmount() - 1);
            }

            HashMap<Integer, ItemStack> failedItems = player.getInventory().addItem(newStack);
            if (!failedItems.isEmpty()) {
                for (ItemStack failedItem : failedItems.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), failedItem);
                }
            }
        } else {
            createVillagerBucket(itemStack, clicked, player);
        }

        clicked.remove();
        event.setCancelled(true);
        log("PICKUP", player, clicked, location);
    }

    @EventHandler
    public void onBucketInteract(PlayerInteractEvent event) {
        if (!settings().enabled()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemStack = event.getItem();

        if (!event.getAction().isRightClick() || itemStack == null || !isVillagerBucket(itemStack)) {
            return;
        }

        event.setCancelled(true);
        if (event.getInteractionPoint() == null || event.getClickedBlock() == null) {
            return;
        }

        LivingEntity entity = readBucketEntity(itemStack, player);
        if (entity == null) {
            player.sendMessage(Component.text("This villager bucket is missing entity data.", NamedTextColor.RED));
            return;
        }

        if (!canPlace(player, entity)) {
            player.sendMessage(Component.text("You are not allowed to place this villager.", NamedTextColor.RED));
            return;
        }

        BlockFace clickedFace = event.getBlockFace();
        Location location = event.getInteractionPoint().clone().add(clickedFace.getModX() * 0.5f, 0, clickedFace.getModZ() * 0.5f);
        if (clickedFace == BlockFace.DOWN) {
            location.subtract(0, entity.getHeight(), 0);
        }

        if (player.getWorld().getBlockAt(location.clone().subtract(0, 1, 0)).isSolid()) {
            location.setY(Math.floor(location.getY()));
        }

        if (player.getWorld().getBlockAt(location).isSolid()) {
            location.setY(Math.floor(location.getY()) + 1);
        }

        entity.spawnAt(location, CreatureSpawnEvent.SpawnReason.BUCKET);
        if (player.getGameMode() != GameMode.CREATIVE) {
            clearVillagerBucket(itemStack);
        }

        playPlaceSound(player, entity);
        log("PLACE", player, entity, location);
    }

    public boolean isVillagerBucket(ItemStack itemStack) {
        if (itemStack.getType() != Material.BUCKET || !itemStack.hasItemMeta()) {
            return false;
        }

        PersistentDataContainer dataContainer = itemStack.getItemMeta().getPersistentDataContainer();
        return dataContainer.has(this.villagerDataKey, PersistentDataType.BYTE_ARRAY);
    }

    private void createVillagerBucket(ItemStack itemStack, Entity entity, Player player) {
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);

        switch (entity) {
            case Villager villager -> {
                playPickupSound(player, entity, Sound.ENTITY_VILLAGER_NO);
                setModelData(itemStack, villager.getVillagerType().key().value());
            }
            case ZombieVillager ignored -> {
                playPickupSound(player, entity, Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT);
                setModelData(itemStack, "zombie_villager");
            }
            case WanderingTrader ignored -> {
                playPickupSound(player, entity, Sound.ENTITY_WANDERING_TRADER_NO);
                setModelData(itemStack, "wandering_trader");
            }
            default -> throw new IllegalStateException("Unsupported bucket entity: " + entity.getType());
        }

        itemStack.editMeta(meta -> {
            switch (entity) {
                case Villager villager -> applyVillagerMeta(meta, villager, player);
                case ZombieVillager zombieVillager -> applySimpleMeta(meta, "Zombie Villager In A Bucket", zombieVillager.isAdult());
                case WanderingTrader wanderingTrader -> applySimpleMeta(meta, "Wandering Trader In A Bucket", wanderingTrader.isAdult());
                default -> throw new IllegalStateException("Unsupported bucket entity: " + entity.getType());
            }

            meta.getPersistentDataContainer().set(this.villagerDataKey, PersistentDataType.BYTE_ARRAY, Bukkit.getUnsafe().serializeEntity(entity));
            meta.setMaxStackSize(1);
        });
    }

    private void applyVillagerMeta(ItemMeta meta, Villager villager, Player player) {
        if (settings().harmReputation() && player.hasPermission("villagertrims.harm-reputation")) {
            Reputation reputation = villager.getReputation(player.getUniqueId());
            int minorNegative = reputation.getReputation(ReputationType.MINOR_NEGATIVE);
            reputation.setReputation(ReputationType.MINOR_NEGATIVE, Math.min(200, minorNegative + 25));
            villager.setReputation(player.getUniqueId(), reputation);
        }

        meta.itemName(Component.text("Villager In A Bucket"));
        List<Component> lore = new ArrayList<>();
        lore.add(grayItalic("Level: " + villager.getVillagerLevel()));
        lore.add(grayItalic("Region: " + villager.getVillagerType().getKey()));
        lore.add(grayItalic("Profession: ").append(Component.translatable(villager.getProfession().translationKey())));
        if (!villager.isAdult()) {
            lore.add(grayItalic("Baby"));
        }
        meta.lore(lore);
    }

    private void applySimpleMeta(ItemMeta meta, String name, boolean adult) {
        meta.itemName(Component.text(name));
        if (!adult) {
            meta.lore(List.of(grayItalic("Baby")));
        } else {
            meta.lore(List.of());
        }
    }

    private LivingEntity readBucketEntity(ItemStack itemStack, Player player) {
        byte[] entityBytes = itemStack.getItemMeta().getPersistentDataContainer().get(this.villagerDataKey, PersistentDataType.BYTE_ARRAY);
        if (entityBytes == null) {
            return null;
        }

        Entity entity = Bukkit.getUnsafe().deserializeEntity(entityBytes, player.getWorld());
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        return null;
    }

    private void clearVillagerBucket(ItemStack itemStack) {
        itemStack.unsetData(DataComponentTypes.CUSTOM_MODEL_DATA);
        itemStack.editMeta(meta -> {
            meta.itemName(null);
            Component customName = meta.customName();
            if (customName instanceof TextComponent textComponent && DEFAULT_BUCKET_NAMES.contains(textComponent.content())) {
                meta.customName(null);
            }
            meta.getPersistentDataContainer().remove(this.villagerDataKey);
            meta.setMaxStackSize(null);
            meta.lore(null);
        });
    }

    private boolean canPickup(Player player, Entity entity) {
        return switch (entity.getType()) {
            case VILLAGER -> player.hasPermission("villagertrims.villager.pickup");
            case ZOMBIE_VILLAGER -> player.hasPermission("villagertrims.zombie_villager.pickup");
            case WANDERING_TRADER -> player.hasPermission("villagertrims.wandering_trader.pickup");
            default -> false;
        };
    }

    private boolean canPlace(Player player, Entity entity) {
        return switch (entity.getType()) {
            case VILLAGER -> player.hasPermission("villagertrims.villager.place");
            case ZOMBIE_VILLAGER -> player.hasPermission("villagertrims.zombie_villager.place");
            case WANDERING_TRADER -> player.hasPermission("villagertrims.wandering_trader.place");
            default -> false;
        };
    }

    private void setModelData(ItemStack itemStack, String modelKey) {
        itemStack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(modelKey).build());
    }

    private void playPickupSound(Player player, Entity entity, Sound sound) {
        if (!entity.isSilent()) {
            player.getWorld().playSound(entity.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    private void playPlaceSound(Player player, Entity entity) {
        if (entity.isSilent()) {
            return;
        }

        Sound sound = switch (entity.getType()) {
            case VILLAGER -> Sound.ENTITY_VILLAGER_CELEBRATE;
            case ZOMBIE_VILLAGER -> Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT;
            case WANDERING_TRADER -> Sound.ENTITY_WANDERING_TRADER_YES;
            default -> null;
        };
        if (sound != null) {
            player.getWorld().playSound(entity.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    private Component grayItalic(String text) {
        return Component.text(text, NamedTextColor.GRAY).decorate(TextDecoration.ITALIC);
    }

    private PluginSettings.VillagerBuckets settings() {
        return this.plugin.settings().villagerBuckets();
    }

    private void log(String action, Player player, Entity entity, Location location) {
        String message = String.format("[%s] Player: %s - Entity: %s - Location: %s", action, player.getName(), entity.getType(), location);
        if (settings().consoleLogging()) {
            this.plugin.getLogger().info(message);
        }
        if (settings().fileLogging() && this.logFileWriter != null) {
            try {
                this.logFileWriter.write("[" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "] " + message + System.lineSeparator());
                this.logFileWriter.flush();
            } catch (IOException exception) {
                this.plugin.getLogger().severe("Unable to write villager action log entry: " + exception.getMessage());
            }
        }
    }

    private void closeLogWriter() {
        if (this.logFileWriter == null) {
            return;
        }
        try {
            this.logFileWriter.close();
        } catch (IOException exception) {
            this.plugin.getLogger().severe("Unable to close villager action log file: " + exception.getMessage());
        } finally {
            this.logFileWriter = null;
        }
    }
}
