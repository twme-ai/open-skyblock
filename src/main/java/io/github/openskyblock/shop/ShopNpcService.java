package io.github.openskyblock.shop;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopNpcService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ShopService shops;
    private final NamespacedKey npcKey;
    private final NamespacedKey shopIdKey;

    public ShopNpcService(JavaPlugin plugin, ConfigService configService, TextService text, ShopService shops) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.shops = shops;
        this.npcKey = new NamespacedKey(plugin, "shop_npc");
        this.shopIdKey = new NamespacedKey(plugin, "shop_id");
    }

    public void reload() {
        if (!enabled()) {
            removeLoadedNpcs();
            return;
        }
        reconcileLoadedNpcs();
        spawnConfiguredNpcs();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.physical-shop-npcs", true)
                && configService.main().getBoolean("features.npc-shops", true);
    }

    public void spawnConfiguredNpcs() {
        if (!enabled()) {
            return;
        }
        for (ShopDefinition shop : shops.shops()) {
            ensureNpc(shop);
        }
    }

    public void removeLoadedNpcs() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isShopNpc(entity)) {
                    entity.remove();
                }
            }
        }
    }

    public void reconcileLoadedNpcs() {
        Set<String> kept = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!isShopNpc(entity)) {
                    continue;
                }
                String shopId = shopId(entity).orElse("");
                ShopDefinition shop = shops.shop(shopId).orElse(null);
                if (shop == null || !shop.npc().enabled() || !matchesConfiguredLocation(entity, shop)) {
                    entity.remove();
                    continue;
                }
                if (!kept.add(shop.id())) {
                    entity.remove();
                    continue;
                }
                prepareNpcEntity(entity, shop);
            }
        }
    }

    public void reconcileChunk(Chunk chunk) {
        Set<String> seen = new HashSet<>();
        for (Entity entity : chunk.getEntities()) {
            if (!isShopNpc(entity)) {
                continue;
            }
            String shopId = shopId(entity).orElse("");
            ShopDefinition shop = shops.shop(shopId).orElse(null);
            if (shop == null || !shop.npc().enabled() || !matchesConfiguredLocation(entity, shop)) {
                entity.remove();
                continue;
            }
            if (!seen.add(shop.id()) && hasMatchingLoadedNpc(shop, entity)) {
                entity.remove();
                continue;
            }
            prepareNpcEntity(entity, shop);
        }
    }

    public boolean handleInteract(Player player, Entity entity) {
        if (!isShopNpc(entity)) {
            return false;
        }
        String shopId = shopId(entity).orElse("");
        ShopDefinition shop = shops.shop(shopId).orElse(null);
        if (shop == null) {
            return true;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getServer().dispatchCommand(player, "skyblock shop " + shop.id().toLowerCase(Locale.ROOT)));
        return true;
    }

    public boolean isShopNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    public Optional<String> shopId(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(shopIdKey, PersistentDataType.STRING));
    }

    private void ensureNpc(ShopDefinition shop) {
        if (!shop.npc().enabled()) {
            return;
        }
        World world = Bukkit.getWorld(shop.npc().worldName());
        if (world == null) {
            return;
        }
        Location location = location(shop);
        if (findMatchingNpc(shop, world).isPresent()) {
            return;
        }
        EntityType entityType = shop.npc().entityType().isAlive() ? shop.npc().entityType() : EntityType.VILLAGER;
        Entity entity = world.spawnEntity(location, entityType);
        tag(entity, shop);
        prepareNpcEntity(entity, shop);
    }

    private Optional<Entity> findMatchingNpc(ShopDefinition shop, World world) {
        return world.getEntities().stream()
                .filter(this::isShopNpc)
                .filter(entity -> shop.id().equals(shopId(entity).orElse("")))
                .filter(entity -> matchesConfiguredLocation(entity, shop))
                .findFirst();
    }

    private boolean hasMatchingLoadedNpc(ShopDefinition shop, Entity excluded) {
        World world = excluded.getWorld();
        return world.getEntities().stream()
                .filter(entity -> entity != excluded)
                .filter(this::isShopNpc)
                .filter(entity -> shop.id().equals(shopId(entity).orElse("")))
                .anyMatch(entity -> matchesConfiguredLocation(entity, shop));
    }

    private void tag(Entity entity, ShopDefinition shop) {
        entity.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(shopIdKey, PersistentDataType.STRING, shop.id());
    }

    private void prepareNpcEntity(Entity entity, ShopDefinition shop) {
        tag(entity, shop);
        entity.customName(text.deserialize(shop.displayName()));
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setPersistent(true);
        entity.teleport(location(shop));
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setAI(false);
            livingEntity.setCollidable(false);
            livingEntity.setCanPickupItems(false);
            livingEntity.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Mob mob) {
            mob.setAware(false);
        }
        if (entity instanceof Villager villager) {
            villager.setProfession(shop.npc().profession());
            villager.setVillagerLevel(1);
            villager.setVillagerExperience(0);
        }
    }

    private boolean matchesConfiguredLocation(Entity entity, ShopDefinition shop) {
        if (!entity.getWorld().getName().equals(shop.npc().worldName())) {
            return false;
        }
        Location expected = location(shop);
        Location actual = entity.getLocation();
        return actual.getBlockX() == expected.getBlockX()
                && actual.getBlockY() == expected.getBlockY()
                && actual.getBlockZ() == expected.getBlockZ();
    }

    private Location location(ShopDefinition shop) {
        World world = Bukkit.getWorld(shop.npc().worldName());
        if (world == null) {
            throw new IllegalStateException("Shop NPC world is not loaded: " + shop.npc().worldName());
        }
        return new Location(
                world,
                shop.npc().x(),
                shop.npc().y(),
                shop.npc().z(),
                shop.npc().yaw(),
                shop.npc().pitch()
        );
    }
}
