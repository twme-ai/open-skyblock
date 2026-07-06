package io.github.openskyblock.mobspawn;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.mob.SkyBlockMobDefinition;
import io.github.openskyblock.mob.MobService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobSpawnService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final MobService mobs;
    private final NamespacedKey zoneKey;
    private final Map<String, MobSpawnZoneDefinition> zones = new HashMap<>();
    private final Map<String, Set<UUID>> spawnedByZone = new HashMap<>();
    private final Map<String, Long> nextSpawnTick = new HashMap<>();
    private long tickIntervalTicks = 100L;
    private long currentTick;

    public MobSpawnService(JavaPlugin plugin, ConfigService configService, TextService text, MobService mobs) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.mobs = mobs;
        this.zoneKey = new NamespacedKey(plugin, "mob_spawn_zone");
    }

    public void reload() {
        zones.clear();
        this.tickIntervalTicks = Math.max(20L, configService.mobSpawns().getLong("settings.tick-interval-ticks", 100L));
        ConfigurationSection section = configService.mobSpawns().getConfigurationSection("zones");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection zoneSection = section.getConfigurationSection(id);
            if (zoneSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            List<MobSpawnEntry> entries = readEntries(zoneSection.getConfigurationSection("mobs"));
            if (entries.isEmpty()) {
                plugin.getLogger().warning("Skipping mob spawn zone without mobs in mob_spawns.yml: " + id);
                continue;
            }
            zones.put(normalized, new MobSpawnZoneDefinition(
                    normalized,
                    zoneSection.getString("display-name", id),
                    zoneSection.getString("world", "world"),
                    zoneSection.getDouble("center.x", 0.0D),
                    zoneSection.getDouble("center.y", 64.0D),
                    zoneSection.getDouble("center.z", 0.0D),
                    Math.max(0.0D, zoneSection.getDouble("radius.x", 8.0D)),
                    Math.max(0.0D, zoneSection.getDouble("radius.y", 0.0D)),
                    Math.max(0.0D, zoneSection.getDouble("radius.z", 8.0D)),
                    zoneSection.getBoolean("use-highest-block", false),
                    zoneSection.getBoolean("load-chunks", false),
                    Math.max(0.0D, zoneSection.getDouble("activation-radius", 64.0D)),
                    Math.max(0, zoneSection.getInt("min-players", 1)),
                    Math.max(1, zoneSection.getInt("max-alive", 10)),
                    Math.max(1, zoneSection.getInt("batch-size", 2)),
                    Math.max(tickIntervalTicks, zoneSection.getLong("interval-ticks", 200L)),
                    List.copyOf(entries)
            ));
            nextSpawnTick.putIfAbsent(normalized, 0L);
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.mob-spawn-zones", true);
    }

    public long tickIntervalTicks() {
        return tickIntervalTicks;
    }

    public List<MobSpawnZoneDefinition> zones() {
        return zones.values().stream()
                .sorted(Comparator.comparing(MobSpawnZoneDefinition::id))
                .toList();
    }

    public List<String> zoneIds() {
        return zones().stream().map(MobSpawnZoneDefinition::id).toList();
    }

    public Optional<MobSpawnZoneDefinition> zone(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(zones.get(id.toUpperCase(Locale.ROOT)));
    }

    public void tick() {
        currentTick += tickIntervalTicks;
        if (!enabled() || !mobs.enabled()) {
            return;
        }
        for (MobSpawnZoneDefinition zone : zones()) {
            long next = nextSpawnTick.getOrDefault(zone.id(), 0L);
            if (currentTick < next) {
                cleanup(zone);
                continue;
            }
            nextSpawnTick.put(zone.id(), currentTick + zone.intervalTicks());
            spawnZone(zone, zone.batchSize(), false);
        }
    }

    public int spawn(Player player, MobSpawnZoneDefinition zone, int requestedAmount) {
        if (!enabled()) {
            text.send(player, "commands.mob-zone-disabled");
            return 0;
        }
        int amount = Math.max(1, Math.min(requestedAmount, zone.maxAlive()));
        int spawned = spawnZone(zone, amount, true);
        if (spawned <= 0) {
            text.send(player, "commands.mob-zone-unavailable", List.of(TextService.parsed("zone", zone.displayName())));
        }
        return spawned;
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mob-zone-disabled");
            return;
        }
        List<MobSpawnZoneDefinition> values = zones();
        if (values.isEmpty()) {
            text.send(player, "commands.mob-zone-empty");
            return;
        }
        text.send(player, "commands.mob-zone-header");
        for (MobSpawnZoneDefinition zone : values) {
            text.send(player, "commands.mob-zone-line", List.of(
                    TextService.raw("id", zone.id()),
                    TextService.parsed("zone", zone.displayName()),
                    TextService.raw("world", zone.worldName()),
                    TextService.raw("alive", Integer.toString(aliveCount(zone))),
                    TextService.raw("max_alive", Integer.toString(zone.maxAlive())),
                    TextService.raw("mobs", Integer.toString(zone.mobs().size()))
            ));
        }
    }

    public void sendDetail(Player player, String id) {
        if (!enabled()) {
            text.send(player, "commands.mob-zone-disabled");
            return;
        }
        MobSpawnZoneDefinition zone = zone(id).orElse(null);
        if (zone == null) {
            text.send(player, "commands.mob-zone-unknown", List.of(TextService.raw("zone", id)));
            return;
        }
        text.send(player, "commands.mob-zone-detail", List.of(
                TextService.raw("id", zone.id()),
                TextService.parsed("zone", zone.displayName()),
                TextService.raw("world", zone.worldName()),
                TextService.raw("center", formatLocation(zone)),
                TextService.raw("alive", Integer.toString(aliveCount(zone))),
                TextService.raw("max_alive", Integer.toString(zone.maxAlive())),
                TextService.raw("interval", Long.toString(zone.intervalTicks())),
                TextService.raw("batch", Integer.toString(zone.batchSize()))
        ));
    }

    private int spawnZone(MobSpawnZoneDefinition zone, int requestedAmount, boolean ignorePlayerGate) {
        World world = Bukkit.getWorld(zone.worldName());
        if (world == null) {
            return 0;
        }
        int alive = aliveCount(zone);
        int capacity = Math.max(0, zone.maxAlive() - alive);
        if (capacity <= 0) {
            return 0;
        }
        if (!ignorePlayerGate && !hasEnoughPlayers(zone, world)) {
            return 0;
        }
        int amount = Math.min(Math.max(1, requestedAmount), capacity);
        int spawned = 0;
        int attempts = amount * 5;
        while (spawned < amount && attempts-- > 0) {
            SkyBlockMobDefinition mob = pickMob(zone).orElse(null);
            if (mob == null) {
                continue;
            }
            Location location = randomLocation(world, zone);
            if (!zone.loadChunks() && !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            if (zone.loadChunks()) {
                Chunk chunk = location.getChunk();
                chunk.load();
            }
            LivingEntity entity = mobs.spawn(location, mob);
            entity.getPersistentDataContainer().set(zoneKey, PersistentDataType.STRING, zone.id());
            spawnedByZone.computeIfAbsent(zone.id(), ignored -> new HashSet<>()).add(entity.getUniqueId());
            spawned++;
        }
        return spawned;
    }

    private Optional<SkyBlockMobDefinition> pickMob(MobSpawnZoneDefinition zone) {
        int totalWeight = zone.mobs().stream().mapToInt(entry -> Math.max(1, entry.weight())).sum();
        if (totalWeight <= 0) {
            return Optional.empty();
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (MobSpawnEntry entry : zone.mobs()) {
            roll -= Math.max(1, entry.weight());
            if (roll < 0) {
                return mobs.definition(entry.mobId());
            }
        }
        return Optional.empty();
    }

    private Location randomLocation(World world, MobSpawnZoneDefinition zone) {
        double x = zone.centerX() + offset(zone.radiusX());
        double z = zone.centerZ() + offset(zone.radiusZ());
        double y = zone.useHighestBlock() ? world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1.0D : zone.centerY() + offset(zone.radiusY());
        return new Location(world, x, y, z);
    }

    private double offset(double radius) {
        if (radius <= 0.0D) {
            return 0.0D;
        }
        return ThreadLocalRandom.current().nextDouble(-radius, radius + Double.MIN_VALUE);
    }

    private int aliveCount(MobSpawnZoneDefinition zone) {
        return cleanup(zone);
    }

    private int cleanup(MobSpawnZoneDefinition zone) {
        Set<UUID> tracked = spawnedByZone.computeIfAbsent(zone.id(), ignored -> new HashSet<>());
        tracked.removeIf(uuid -> {
            Entity entity = Bukkit.getEntity(uuid);
            return entity == null || !entity.isValid() || entity.isDead();
        });
        return tracked.size();
    }

    private boolean hasEnoughPlayers(MobSpawnZoneDefinition zone, World world) {
        if (zone.minPlayers() <= 0) {
            return true;
        }
        int nearby = 0;
        double radiusSquared = zone.activationRadius() * zone.activationRadius();
        Location center = new Location(world, zone.centerX(), zone.centerY(), zone.centerZ());
        for (Player player : world.getPlayers()) {
            if (zone.activationRadius() <= 0.0D || player.getLocation().distanceSquared(center) <= radiusSquared) {
                nearby++;
            }
            if (nearby >= zone.minPlayers()) {
                return true;
            }
        }
        return false;
    }

    private List<MobSpawnEntry> readEntries(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<MobSpawnEntry> entries = new ArrayList<>();
        for (String mobId : section.getKeys(false)) {
            int weight = Math.max(1, section.getInt(mobId + ".weight", 1));
            entries.add(new MobSpawnEntry(mobId.toUpperCase(Locale.ROOT), weight));
        }
        return entries;
    }

    private String formatLocation(MobSpawnZoneDefinition zone) {
        return text.formatNumber(zone.centerX()) + ", " + text.formatNumber(zone.centerY()) + ", " + text.formatNumber(zone.centerZ());
    }
}
