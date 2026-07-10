package io.github.openskyblock.dragon;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class DragonService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final NamespacedKey liveDragonKey;
    private final NamespacedKey dragonOwnerKey;
    private final NamespacedKey dragonTypeKey;
    private final NamespacedKey abilityProjectileKey;
    private final NamespacedKey abilityIdKey;
    private final NamespacedKey abilityEncounterKey;
    private final NamespacedKey abilityDamageKey;
    private final NamespacedKey abilityRadiusKey;
    private final Map<String, DragonDefinition> dragons = new HashMap<>();
    private final Map<String, DragonPhaseDefinition> phases = new HashMap<>();
    private final Map<String, DragonAbilityDefinition> abilities = new HashMap<>();
    private final Map<UUID, ActiveDragonEncounter> activeEncounters = new HashMap<>();
    private final Map<UUID, UUID> activeByOwner = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private int eyesRequired = 8;
    private int maxPlacedEyes = 8;
    private String summoningEyeItemId = "SUMMONING_EYE";
    private double baseCoinReward = 5000.0D;
    private double damageForRankOne = 1_000_000.0D;
    private double damageForRankTwo = 500_000.0D;
    private double damageForRankThree = 100_000.0D;
    private double skyBlockXpPerKill = 0.25D;
    private double skyBlockXpPerUniqueDragon = 2.0D;
    private boolean liveEnabled = true;
    private boolean autoSpawnOnReady = true;
    private boolean allowSimulatedFights = true;
    private boolean onePerWorld = true;
    private boolean bossBarEnabled = true;
    private boolean refundEyesOnTimeout;
    private boolean refundEyesOnShutdown = true;
    private boolean broadcastSpawn = true;
    private boolean broadcastDefeat = true;
    private long liveTimeoutMillis = 600_000L;
    private double participantRadius = 160.0D;
    private double minimumRewardDamage = 1.0D;
    private double physicalHealth = 200.0D;
    private double spawnOffsetX;
    private double spawnOffsetY = 20.0D;
    private double spawnOffsetZ;
    private boolean combatEnabled = true;
    private boolean enforceConfiguredPhases = true;
    private long combatTickIntervalTicks = 5L;
    private List<String> defaultAbilityIds = List.of("RUSH", "FIREBALL", "LIGHTNING");
    private BossBar.Color bossBarColor = BossBar.Color.PURPLE;
    private BossBar.Overlay bossBarOverlay = BossBar.Overlay.PROGRESS;

    public DragonService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
        this.liveDragonKey = new NamespacedKey(plugin, "live_dragon");
        this.dragonOwnerKey = new NamespacedKey(plugin, "dragon_owner");
        this.dragonTypeKey = new NamespacedKey(plugin, "dragon_type");
        this.abilityProjectileKey = new NamespacedKey(plugin, "dragon_ability_projectile");
        this.abilityIdKey = new NamespacedKey(plugin, "dragon_ability_id");
        this.abilityEncounterKey = new NamespacedKey(plugin, "dragon_ability_encounter");
        this.abilityDamageKey = new NamespacedKey(plugin, "dragon_ability_damage");
        this.abilityRadiusKey = new NamespacedKey(plugin, "dragon_ability_radius");
    }

    public void reload() {
        eyesRequired = Math.max(1, configService.dragons().getInt("settings.eyes-required", 8));
        maxPlacedEyes = Math.max(eyesRequired, configService.dragons().getInt("settings.max-placed-eyes", 8));
        summoningEyeItemId = configService.dragons().getString("settings.summoning-eye-item", "SUMMONING_EYE").toUpperCase(Locale.ROOT);
        baseCoinReward = Math.max(0.0D, configService.dragons().getDouble("settings.base-coin-reward", 5000.0D));
        damageForRankOne = Math.max(1.0D, configService.dragons().getDouble("settings.damage-ranks.first", 1_000_000.0D));
        damageForRankTwo = Math.max(1.0D, configService.dragons().getDouble("settings.damage-ranks.second", 500_000.0D));
        damageForRankThree = Math.max(1.0D, configService.dragons().getDouble("settings.damage-ranks.third", 100_000.0D));
        skyBlockXpPerKill = Math.max(0.0D, configService.dragons().getDouble("settings.skyblock-xp-per-kill", 0.25D));
        skyBlockXpPerUniqueDragon = Math.max(0.0D, configService.dragons().getDouble("settings.skyblock-xp-per-unique-dragon", 2.0D));
        ConfigurationSection live = configService.dragons().getConfigurationSection("settings.live");
        liveEnabled = live == null || live.getBoolean("enabled", true);
        autoSpawnOnReady = live == null || live.getBoolean("auto-spawn-on-ready", true);
        allowSimulatedFights = live == null || live.getBoolean("allow-simulated-fights", true);
        onePerWorld = live == null || live.getBoolean("one-per-world", true);
        bossBarEnabled = live == null || live.getBoolean("boss-bar.enabled", true);
        refundEyesOnTimeout = live != null && live.getBoolean("refund-eyes-on-timeout", false);
        refundEyesOnShutdown = live == null || live.getBoolean("refund-eyes-on-shutdown", true);
        broadcastSpawn = live == null || live.getBoolean("broadcast-spawn", true);
        broadcastDefeat = live == null || live.getBoolean("broadcast-defeat", true);
        liveTimeoutMillis = Math.max(10L, live == null ? 600L : live.getLong("timeout-seconds", 600L)) * 1000L;
        participantRadius = Math.max(1.0D, live == null ? 160.0D : live.getDouble("participant-radius", 160.0D));
        minimumRewardDamage = Math.max(0.0D, live == null ? 1.0D : live.getDouble("minimum-reward-damage", 1.0D));
        physicalHealth = Math.max(1.0D, Math.min(1024.0D, live == null ? 200.0D : live.getDouble("physical-health", 200.0D)));
        spawnOffsetX = live == null ? 0.0D : live.getDouble("spawn-offset.x", 0.0D);
        spawnOffsetY = live == null ? 20.0D : live.getDouble("spawn-offset.y", 20.0D);
        spawnOffsetZ = live == null ? 0.0D : live.getDouble("spawn-offset.z", 0.0D);
        bossBarColor = parseBossBarColor(live == null ? "PURPLE" : live.getString("boss-bar.color", "PURPLE"));
        bossBarOverlay = parseBossBarOverlay(live == null ? "PROGRESS" : live.getString("boss-bar.overlay", "PROGRESS"));
        loadCombat(live == null ? null : live.getConfigurationSection("combat"));
        loadDragons();
        cleanupStaleEntities();
        if (!liveEnabled && !activeEncounters.isEmpty()) {
            shutdown();
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.dragons", true);
    }

    public long tickIntervalTicks() {
        return combatTickIntervalTicks;
    }

    public List<String> dragonIds() {
        return definitions().stream().map(DragonDefinition::id).toList();
    }

    public Optional<DragonDefinition> dragon(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dragons.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<DragonDefinition> definitions() {
        return dragons.values().stream()
                .sorted(Comparator.comparing(DragonDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dragon-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        ActiveDragonEncounter encounter = activeForOwner(player.getUniqueId()).orElse(null);
        text.send(player, "commands.dragon-status", List.of(
                TextService.raw("eyes", Integer.toString(profile.placedDragonEyes())),
                TextService.raw("eyes_required", Integer.toString(eyesRequired)),
                TextService.raw("kills", text.formatNumber(profile.totalDragonKills())),
                TextService.raw("eyes_used", text.formatNumber(profile.summoningEyesUsed())),
                TextService.raw("best_damage", text.formatNumber(profile.bestDragonDamage())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile))),
                TextService.parsed("live_status", encounter == null ? text.rawMessage("dragons.live-inactive") : text.rawMessage("dragons.live-active")),
                TextService.parsed("live_dragon", encounter == null ? text.rawMessage("dragons.none") : dragon(encounter.dragonId()).map(DragonDefinition::displayName).orElse(encounter.dragonId())),
                TextService.parsed("live_phase", encounter == null
                        ? text.rawMessage("dragons.phase-none")
                        : currentPhase(encounter).map(DragonPhaseDefinition::displayName).orElse(text.rawMessage("dragons.phase-none"))),
                TextService.raw("live_health", text.formatNumber(encounter == null ? 0.0D : encounter.currentHealth())),
                TextService.raw("live_time", Long.toString(encounter == null ? 0L : remainingSeconds(encounter.expiresAtMillis())))
        ));
        for (DragonDefinition definition : definitions()) {
            text.send(player, "commands.dragon-status-line", dragonPlaceholders(profile, definition));
        }
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dragon-disabled");
            return;
        }
        if (dragons.isEmpty()) {
            text.send(player, "commands.dragon-empty");
            return;
        }
        text.send(player, "commands.dragon-list-header");
        for (DragonDefinition definition : definitions()) {
            text.send(player, "commands.dragon-list-line", dragonPlaceholders(profiles.profile(player), definition));
        }
    }

    public boolean placeEyes(Player player, int amount) {
        if (!enabled()) {
            text.send(player, "commands.dragon-disabled");
            return false;
        }
        if (amount <= 0) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        if (activeForOwner(player.getUniqueId()).isPresent()) {
            text.send(player, "commands.dragon-live-already-active");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int space = Math.max(0, maxPlacedEyes - profile.placedDragonEyes());
        int placing = Math.min(amount, space);
        if (placing <= 0) {
            text.send(player, "commands.dragon-altar-full", altarPlaceholders(profile));
            return false;
        }
        if (countItem(player, summoningEyeItemId) < placing) {
            text.send(player, "commands.dragon-no-eyes", List.of(TextService.raw("amount", Integer.toString(placing))));
            return false;
        }
        consumeItem(player, summoningEyeItemId, placing);
        profile.placedDragonEyes(profile.placedDragonEyes() + placing);
        profile.addSummoningEyesUsed(placing);
        profiles.save(player);
        text.send(player, "commands.dragon-eyes-placed", altarPlaceholders(profile));
        if (profile.placedDragonEyes() >= eyesRequired) {
            if (liveEnabled && autoSpawnOnReady) {
                spawnLive(player, "");
            } else if (liveEnabled) {
                text.send(player, "commands.dragon-live-ready", altarPlaceholders(profile));
            } else {
                text.send(player, "commands.dragon-ready", altarPlaceholders(profile));
            }
        }
        return true;
    }

    public boolean fight(Player player, double damage, String requestedDragonId) {
        if (!enabled()) {
            text.send(player, "commands.dragon-disabled");
            return false;
        }
        if (damage <= 0.0D || Double.isNaN(damage) || Double.isInfinite(damage)) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        if (!allowSimulatedFights) {
            text.send(player, "commands.dragon-simulated-disabled");
            return false;
        }
        if (activeForOwner(player.getUniqueId()).isPresent()) {
            text.send(player, "commands.dragon-live-already-active");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.placedDragonEyes() < eyesRequired) {
            text.send(player, "commands.dragon-not-ready", altarPlaceholders(profile));
            return false;
        }
        DragonDefinition dragon = requestedDragonId == null || requestedDragonId.isBlank()
                ? selectDragon().orElse(null)
                : dragon(requestedDragonId).orElse(null);
        if (dragon == null) {
            text.send(player, "commands.dragon-unknown", List.of(TextService.raw("dragon", requestedDragonId == null ? "" : requestedDragonId)));
            return false;
        }
        int eyes = profile.placedDragonEyes();
        int rank = damageRank(damage);
        profile.placedDragonEyes(0);
        settleRewards(player, profile, dragon, eyes, damage, rank);
        return true;
    }

    public boolean spawnLive(Player player, String requestedDragonId) {
        if (!enabled() || !liveEnabled) {
            text.send(player, "commands.dragon-live-disabled");
            return false;
        }
        if (activeForOwner(player.getUniqueId()).isPresent()) {
            text.send(player, "commands.dragon-live-already-active");
            return false;
        }
        if (onePerWorld && activeEncounters.values().stream()
                .anyMatch(encounter -> encounter.worldId().equals(player.getWorld().getUID()) && activeEntity(encounter).isPresent())) {
            text.send(player, "commands.dragon-live-world-active");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.placedDragonEyes() < eyesRequired) {
            text.send(player, "commands.dragon-not-ready", altarPlaceholders(profile));
            return false;
        }
        DragonDefinition definition = requestedDragonId == null || requestedDragonId.isBlank()
                ? selectDragon().orElse(null)
                : dragon(requestedDragonId).orElse(null);
        if (definition == null) {
            text.send(player, "commands.dragon-unknown", List.of(TextService.raw("dragon", requestedDragonId == null ? "" : requestedDragonId)));
            return false;
        }
        Location location = player.getLocation().clone().add(spawnOffsetX, spawnOffsetY, spawnOffsetZ);
        int minimumY = player.getWorld().getMinHeight() + 2;
        int maximumY = player.getWorld().getMaxHeight() - 2;
        location.setY(Math.max(minimumY, Math.min(maximumY, location.getY())));
        location.getChunk().load();
        EnderDragon entity;
        try {
            entity = player.getWorld().spawn(
                    location,
                    EnderDragon.class,
                    CreatureSpawnEvent.SpawnReason.CUSTOM,
                    true,
                    dragon -> configureLiveEntity(dragon, player, definition)
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not spawn live dragon " + definition.id() + ": " + exception.getMessage());
            text.send(player, "commands.dragon-live-spawn-failed");
            return false;
        }
        long now = System.currentTimeMillis();
        int eyes = profile.placedDragonEyes();
        ActiveDragonEncounter encounter = new ActiveDragonEncounter(
                entity.getUniqueId(),
                player.getUniqueId(),
                entity.getWorld().getUID(),
                definition.id(),
                eyes,
                definition.health(),
                now,
                now + liveTimeoutMillis
        );
        activeEncounters.put(entity.getUniqueId(), encounter);
        activeByOwner.put(player.getUniqueId(), entity.getUniqueId());
        profile.placedDragonEyes(0);
        profiles.save(player);
        syncEntityHealth(entity, encounter);
        updateCombatState(entity, encounter, definition, now);
        updateBossBar(entity, encounter);
        List<TextService.TextPlaceholder> placeholders = livePlaceholders(encounter, definition, player.getName());
        if (broadcastSpawn) {
            broadcast("commands.dragon-live-spawned", placeholders);
        } else {
            text.send(player, "commands.dragon-live-spawned", placeholders);
        }
        return true;
    }

    public boolean cancelLive(Player player) {
        ActiveDragonEncounter encounter = activeForOwner(player.getUniqueId()).orElse(null);
        if (encounter == null) {
            text.send(player, "commands.dragon-live-none");
            return false;
        }
        removeEncounter(encounter, true);
        text.send(player, "commands.dragon-live-cancelled", List.of(TextService.raw("eyes", Integer.toString(encounter.eyes()))));
        return true;
    }

    public boolean isLiveDragon(Entity entity) {
        return liveDragonEntity(entity).isPresent();
    }

    public boolean damageLiveDragon(Entity entity, Player player, double damage) {
        EnderDragon dragonEntity = liveDragonEntity(entity).orElse(null);
        ActiveDragonEncounter encounter = dragonEntity == null ? null : activeEncounters.get(dragonEntity.getUniqueId());
        if (encounter == null || encounter.completing() || player == null || damage <= 0.0D) {
            return false;
        }
        DragonDefinition definition = dragon(encounter.dragonId()).orElse(null);
        if (definition == null) {
            return false;
        }
        if (!encounter.worldId().equals(player.getWorld().getUID())
                || player.getLocation().distanceSquared(dragonEntity.getLocation()) > participantRadius * participantRadius) {
            return false;
        }
        double adjustedDamage = combatEnabled
                ? damage * definition.combat().incomingDamageMultiplier()
                : damage;
        double applied = encounter.applyDamage(player.getUniqueId(), adjustedDamage);
        if (applied <= 0.0D) {
            return false;
        }
        syncEntityHealth(dragonEntity, encounter);
        updateBossBar(dragonEntity, encounter);
        if (encounter.currentHealth() <= 0.0D && encounter.beginCompletion()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> completeLive(encounter.entityId()));
        }
        return true;
    }

    public double scaleLiveDragonDamage(Entity damager, double damage) {
        EnderDragon dragonEntity = liveDragonEntity(damager).orElse(null);
        if (dragonEntity == null || damage <= 0.0D || !combatEnabled) {
            return damage;
        }
        ActiveDragonEncounter encounter = activeEncounters.get(dragonEntity.getUniqueId());
        DragonDefinition definition = encounter == null ? null : dragon(encounter.dragonId()).orElse(null);
        if (encounter == null || definition == null) {
            return damage;
        }
        double phaseMultiplier = currentPhase(encounter)
                .map(DragonPhaseDefinition::outgoingDamageMultiplier)
                .orElse(1.0D);
        return damage * definition.combat().outgoingDamageMultiplier() * phaseMultiplier;
    }

    public EnderDragon.Phase desiredPhase(Entity entity) {
        EnderDragon dragonEntity = liveDragonEntity(entity).orElse(null);
        if (dragonEntity == null || !combatEnabled || !enforceConfiguredPhases) {
            return null;
        }
        ActiveDragonEncounter encounter = activeEncounters.get(dragonEntity.getUniqueId());
        if (encounter == null) {
            return null;
        }
        DragonAbilityDefinition activeAbility = abilities.get(encounter.activeAbilityId());
        if (activeAbility != null && encounter.hasActiveAbility(System.currentTimeMillis())) {
            return activeAbility.nativePhase();
        }
        return currentPhase(encounter)
                .map(DragonPhaseDefinition::nativePhase)
                .orElse(EnderDragon.Phase.CIRCLING);
    }

    public boolean isAbilityProjectile(Entity entity) {
        return entity instanceof Projectile
                && entity.getPersistentDataContainer().has(abilityProjectileKey, PersistentDataType.BYTE);
    }

    public void handleAbilityProjectileImpact(Projectile projectile, Location impactLocation) {
        if (!isAbilityProjectile(projectile) || !projectile.isValid()) {
            return;
        }
        String encounterValue = projectile.getPersistentDataContainer().get(abilityEncounterKey, PersistentDataType.STRING);
        String abilityId = projectile.getPersistentDataContainer().get(abilityIdKey, PersistentDataType.STRING);
        Double damage = projectile.getPersistentDataContainer().get(abilityDamageKey, PersistentDataType.DOUBLE);
        Double radius = projectile.getPersistentDataContainer().get(abilityRadiusKey, PersistentDataType.DOUBLE);
        projectile.remove();
        UUID encounterId = parseUuid(encounterValue).orElse(null);
        ActiveDragonEncounter encounter = encounterId == null ? null : activeEncounters.get(encounterId);
        EnderDragon dragonEntity = encounter == null ? null : activeEntity(encounter).orElse(null);
        DragonAbilityDefinition ability = abilityId == null ? null : abilities.get(abilityId);
        if (encounter == null || dragonEntity == null || ability == null || impactLocation == null) {
            return;
        }
        double impactRadius = Math.max(0.5D, radius == null ? ability.radius() : radius);
        showAbilityEffect(impactLocation, ability, impactRadius);
        damagePlayers(dragonEntity, impactLocation, impactRadius, damage == null ? ability.damage() : damage, 0);
    }

    public void handleExternalDeath(Entity entity) {
        ActiveDragonEncounter encounter = entity == null ? null : activeEncounters.get(entity.getUniqueId());
        if (encounter != null && encounter.beginCompletion()) {
            completeLive(encounter.entityId());
        }
    }

    public void tickLiveEncounters() {
        if (activeEncounters.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ActiveDragonEncounter encounter : List.copyOf(activeEncounters.values())) {
            EnderDragon entity = activeEntity(encounter).orElse(null);
            if (entity == null) {
                Player owner = plugin.getServer().getPlayer(encounter.ownerId());
                removeEncounter(encounter, true);
                if (owner != null) {
                    text.send(owner, "commands.dragon-live-missing-refunded", List.of(TextService.raw("eyes", Integer.toString(encounter.eyes()))));
                }
                continue;
            }
            if (now >= encounter.expiresAtMillis()) {
                Player owner = plugin.getServer().getPlayer(encounter.ownerId());
                removeEncounter(encounter, refundEyesOnTimeout);
                if (owner != null) {
                    text.send(
                            owner,
                            refundEyesOnTimeout ? "commands.dragon-live-timeout-refunded" : "commands.dragon-live-timeout",
                            List.of(TextService.raw("eyes", Integer.toString(encounter.eyes())))
                    );
                }
                continue;
            }
            if (encounter.completing()) {
                syncEntityHealth(entity, encounter);
                updateBossBar(entity, encounter);
                continue;
            }
            DragonDefinition definition = dragon(encounter.dragonId()).orElse(null);
            if (definition == null) {
                removeEncounter(encounter, true);
                continue;
            }
            updateCombatState(entity, encounter, definition, now);
            syncEntityHealth(entity, encounter);
            updateBossBar(entity, encounter);
        }
    }

    public void playerJoined(Player player) {
        for (ActiveDragonEncounter encounter : activeEncounters.values()) {
            activeEntity(encounter).ifPresent(entity -> updateBossBar(entity, encounter));
        }
    }

    public void playerQuit(Player player) {
        for (BossBar bossBar : bossBars.values()) {
            player.hideBossBar(bossBar);
        }
    }

    public void shutdown() {
        for (ActiveDragonEncounter encounter : List.copyOf(activeEncounters.values())) {
            removeEncounter(encounter, refundEyesOnShutdown);
        }
        bossBars.clear();
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        return profile.totalDragonKills() * skyBlockXpPerKill + profile.dragonKills().keySet().size() * skyBlockXpPerUniqueDragon;
    }

    private void configureLiveEntity(EnderDragon dragon, Player owner, DragonDefinition definition) {
        dragon.getPersistentDataContainer().set(liveDragonKey, PersistentDataType.BYTE, (byte) 1);
        dragon.getPersistentDataContainer().set(dragonOwnerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        dragon.getPersistentDataContainer().set(dragonTypeKey, PersistentDataType.STRING, definition.id());
        dragon.customName(text.deserialize(text.rawMessage("dragons.live-entity-name"), dragonPlaceholders(profiles.profile(owner), definition)));
        dragon.setCustomNameVisible(true);
        dragon.setPersistent(true);
        dragon.setRemoveWhenFarAway(false);
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
        dragon.getBossBar().setVisible(false);
        AttributeInstance health = dragon.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(physicalHealth);
            dragon.setHealth(physicalHealth);
        }
    }

    private void settleRewards(Player player, SkyBlockProfile profile, DragonDefinition dragon, int eyes, double damage, int rank) {
        List<String> rewards = new ArrayList<>();
        giveFragments(player, dragon, eyes, rank).ifPresent(rewards::add);
        selectReward(dragon, eyes, damage).flatMap(reward -> giveReward(player, reward)).ifPresent(rewards::add);
        double coins = baseCoinReward * Math.max(1, 5 - Math.min(4, Math.max(1, rank))) + eyes * 500.0D;
        if (coins > 0.0D) {
            economy.addPurse(player, coins);
        }
        if (dragon.combatXp() > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, dragon.combatXp());
        }
        profile.addDragonKill(dragon.id(), 1);
        profile.recordDragonDamage(damage);
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(dragonPlaceholders(profile, dragon));
        placeholders.add(TextService.raw("damage", text.formatNumber(damage)));
        placeholders.add(TextService.raw("rank", Integer.toString(rank)));
        placeholders.add(TextService.raw("eyes", Integer.toString(eyes)));
        placeholders.add(TextService.raw("coins", text.formatNumber(coins)));
        placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? text.rawMessage("dragons.none") : String.join("<gray>, </gray>", rewards)));
        text.send(player, "commands.dragon-defeated", placeholders);
    }

    private void completeLive(UUID entityId) {
        ActiveDragonEncounter encounter = activeEncounters.get(entityId);
        if (encounter == null) {
            return;
        }
        DragonDefinition definition = dragon(encounter.dragonId()).orElse(null);
        EnderDragon entity = activeEntity(encounter).orElse(null);
        Location location = entity == null ? null : entity.getLocation();
        if (definition != null && entity != null) {
            triggerDeathAbility(entity, encounter, definition);
        }
        removeEncounter(encounter, false);
        if (definition == null) {
            return;
        }
        List<Map.Entry<UUID, Double>> participants = encounter.damageByPlayer().entrySet().stream()
                .filter(entry -> entry.getValue() >= minimumRewardDamage)
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .toList();
        int awarded = 0;
        for (Map.Entry<UUID, Double> participant : participants) {
            Player player = plugin.getServer().getPlayer(participant.getKey());
            if (player == null) {
                continue;
            }
            awarded++;
            int rank = Math.min(4, awarded);
            int eyes = participant.getKey().equals(encounter.ownerId()) ? encounter.eyes() : 0;
            settleRewards(player, profiles.profile(player), definition, eyes, participant.getValue(), rank);
        }
        List<TextService.TextPlaceholder> placeholders = livePlaceholders(encounter, definition, ownerName(encounter.ownerId()));
        List<TextService.TextPlaceholder> completed = new ArrayList<>(placeholders);
        completed.add(TextService.raw("participants", Integer.toString(awarded)));
        completed.add(TextService.raw("top_damage", text.formatNumber(participants.isEmpty() ? 0.0D : participants.getFirst().getValue())));
        if (broadcastDefeat) {
            broadcast("commands.dragon-live-broadcast-defeated", completed);
        } else if (location != null) {
            location.getWorld().getNearbyPlayers(location, participantRadius)
                    .forEach(player -> text.send(player, "commands.dragon-live-broadcast-defeated", completed));
        }
    }

    private void removeEncounter(ActiveDragonEncounter encounter, boolean refundEyes) {
        activeEncounters.remove(encounter.entityId());
        activeByOwner.remove(encounter.ownerId(), encounter.entityId());
        hideBossBar(encounter.entityId());
        removeAbilityProjectiles(encounter.entityId());
        Entity entity = Bukkit.getEntity(encounter.entityId());
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        if (refundEyes) {
            SkyBlockProfile profile = profiles.profile(encounter.ownerId());
            if (profile != null) {
                profile.placedDragonEyes(Math.min(maxPlacedEyes, profile.placedDragonEyes() + encounter.eyes()));
                Player player = plugin.getServer().getPlayer(encounter.ownerId());
                if (player != null) {
                    profiles.save(player);
                } else {
                    profiles.saveAll();
                }
            }
        }
    }

    private Optional<ActiveDragonEncounter> activeForOwner(UUID ownerId) {
        UUID entityId = activeByOwner.get(ownerId);
        if (entityId == null) {
            return Optional.empty();
        }
        ActiveDragonEncounter encounter = activeEncounters.get(entityId);
        if (encounter == null || activeEntity(encounter).isEmpty()) {
            if (encounter != null) {
                removeEncounter(encounter, true);
            } else {
                activeByOwner.remove(ownerId, entityId);
                hideBossBar(entityId);
            }
            return Optional.empty();
        }
        return Optional.of(encounter);
    }

    private Optional<EnderDragon> activeEntity(ActiveDragonEncounter encounter) {
        Entity entity = Bukkit.getEntity(encounter.entityId());
        if (entity instanceof EnderDragon dragon && dragon.isValid() && !dragon.isDead()) {
            return Optional.of(dragon);
        }
        return Optional.empty();
    }

    private Optional<EnderDragon> liveDragonEntity(Entity entity) {
        EnderDragon dragon = null;
        if (entity instanceof EnderDragon enderDragon) {
            dragon = enderDragon;
        } else if (entity instanceof EnderDragonPart part) {
            dragon = part.getParent();
        }
        if (dragon == null || !dragon.getPersistentDataContainer().has(liveDragonKey, PersistentDataType.BYTE)) {
            return Optional.empty();
        }
        return Optional.of(dragon);
    }

    private void updateCombatState(EnderDragon entity, ActiveDragonEncounter encounter, DragonDefinition definition, long now) {
        if (!combatEnabled) {
            return;
        }
        DragonPhaseDefinition phase = phaseFor(encounter).orElse(null);
        if (phase != null && !phase.id().equals(encounter.currentPhaseId())) {
            String previousPhase = encounter.currentPhaseId();
            encounter.currentPhaseId(phase.id());
            if (!encounter.hasActiveAbility(now)) {
                entity.setPhase(phase.nativePhase());
            }
            if (!previousPhase.isBlank() && phase.announce()) {
                sendNearby(entity, "commands.dragon-live-phase-change", phasePlaceholders(encounter, definition, phase));
            }
        }
        updateActiveAbility(entity, encounter, definition, now);
        if (encounter.hasActiveAbility(now)) {
            return;
        }
        scheduleAbilities(entity, encounter, definition, phase, now);
    }

    private void updateActiveAbility(EnderDragon entity, ActiveDragonEncounter encounter, DragonDefinition definition, long now) {
        if (encounter.activeAbilityId().isBlank()) {
            return;
        }
        DragonAbilityDefinition ability = abilities.get(encounter.activeAbilityId());
        if (ability == null || now >= encounter.activeAbilityEndsAtMillis()) {
            finishActiveAbility(entity, encounter);
            return;
        }
        if (ability.type() != DragonAbilityType.RUSH) {
            return;
        }
        Player target = encounter.activeAbilityTargetId() == null
                ? null
                : plugin.getServer().getPlayer(encounter.activeAbilityTargetId());
        if (!validTarget(entity, target, participantRadius)) {
            finishActiveAbility(entity, encounter);
            return;
        }
        Location destination = target.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        Vector movement = destination.toVector().subtract(entity.getLocation().toVector());
        if (movement.lengthSquared() <= ability.radius() * ability.radius()) {
            showAbilityEffect(target.getLocation(), ability, ability.radius());
            target.damage(ability.damage(), entity);
            finishActiveAbility(entity, encounter);
            return;
        }
        if (movement.lengthSquared() > 0.0001D) {
            double speed = Math.max(0.1D, Math.min(4.0D, ability.speed() * definition.combat().movementSpeedMultiplier()));
            entity.setVelocity(movement.normalize().multiply(speed));
            entity.setTarget(target);
        }
    }

    private void finishActiveAbility(EnderDragon entity, ActiveDragonEncounter encounter) {
        encounter.clearActiveAbility();
        currentPhase(encounter).ifPresent(phase -> entity.setPhase(phase.nativePhase()));
    }

    private void scheduleAbilities(
            EnderDragon entity,
            ActiveDragonEncounter encounter,
            DragonDefinition definition,
            DragonPhaseDefinition phase,
            long now
    ) {
        for (String abilityId : definition.combat().abilityIds()) {
            DragonAbilityDefinition ability = abilities.get(abilityId);
            if (ability == null || !ability.availableIn(encounter.currentPhaseId())) {
                continue;
            }
            double cooldownMultiplier = definition.combat().cooldownMultiplier()
                    * (phase == null ? 1.0D : phase.cooldownMultiplier());
            long nextUse = encounter.nextAbilityAtMillis(ability.id());
            if (nextUse <= 0L) {
                encounter.scheduleAbility(ability.id(), now + scaledDuration(ability.initialDelayMillis(), cooldownMultiplier));
                continue;
            }
            if (now < nextUse) {
                continue;
            }
            encounter.scheduleAbility(ability.id(), now + scaledDuration(ability.cooldownMillis(), cooldownMultiplier));
            if (triggerAbility(entity, encounter, definition, ability, now)) {
                break;
            }
        }
    }

    private boolean triggerAbility(
            EnderDragon entity,
            ActiveDragonEncounter encounter,
            DragonDefinition definition,
            DragonAbilityDefinition ability,
            long now
    ) {
        List<Player> targets = selectTargets(entity, ability.range(), Math.max(1, ability.targetCount()));
        if (ability.type() != DragonAbilityType.AREA_BLAST && targets.isEmpty()) {
            return false;
        }
        Player primaryTarget = targets.isEmpty() ? null : targets.getFirst();
        encounter.beginAbility(
                ability.id(),
                primaryTarget == null ? null : primaryTarget.getUniqueId(),
                now + Math.max(250L, ability.durationMillis())
        );
        entity.setPhase(ability.nativePhase());
        switch (ability.type()) {
            case RUSH -> {
                entity.setTarget(primaryTarget);
                Vector movement = primaryTarget.getLocation().clone().add(0.0D, 1.0D, 0.0D).toVector()
                        .subtract(entity.getLocation().toVector());
                if (movement.lengthSquared() > 0.0001D) {
                    double speed = Math.max(0.1D, Math.min(4.0D, ability.speed() * definition.combat().movementSpeedMultiplier()));
                    entity.setVelocity(movement.normalize().multiply(speed));
                }
            }
            case FIREBALL -> spawnAbilityFireball(entity, encounter, ability, primaryTarget);
            case LIGHTNING -> {
                for (Player target : targets) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    showAbilityEffect(target.getLocation(), ability, ability.radius());
                    target.damage(ability.damage(), entity);
                }
            }
            case AREA_BLAST -> {
                showAbilityEffect(entity.getLocation(), ability, ability.radius());
                damagePlayers(entity, entity.getLocation(), ability.radius(), ability.damage(), ability.targetCount());
            }
        }
        if (ability.announce()) {
            sendNearby(entity, "commands.dragon-live-ability", abilityPlaceholders(encounter, definition, ability));
        }
        return true;
    }

    private void spawnAbilityFireball(
            EnderDragon entity,
            ActiveDragonEncounter encounter,
            DragonAbilityDefinition ability,
            Player target
    ) {
        Location origin = entity.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(origin.toVector());
        if (direction.lengthSquared() <= 0.0001D) {
            direction = entity.getLocation().getDirection();
        }
        Vector velocity = direction.normalize().multiply(Math.max(0.1D, Math.min(4.0D, ability.speed())));
        entity.getWorld().spawn(origin, DragonFireball.class, fireball -> {
            fireball.setShooter(entity);
            fireball.setYield(0.0F);
            fireball.setIsIncendiary(false);
            fireball.setDirection(velocity.clone().normalize());
            fireball.setVelocity(velocity);
            fireball.getPersistentDataContainer().set(abilityProjectileKey, PersistentDataType.BYTE, (byte) 1);
            fireball.getPersistentDataContainer().set(abilityIdKey, PersistentDataType.STRING, ability.id());
            fireball.getPersistentDataContainer().set(abilityEncounterKey, PersistentDataType.STRING, encounter.entityId().toString());
            fireball.getPersistentDataContainer().set(abilityDamageKey, PersistentDataType.DOUBLE, ability.damage());
            fireball.getPersistentDataContainer().set(abilityRadiusKey, PersistentDataType.DOUBLE, ability.radius());
        });
    }

    private void triggerDeathAbility(EnderDragon entity, ActiveDragonEncounter encounter, DragonDefinition definition) {
        String deathAbilityId = definition.combat().deathAbilityId();
        if (!combatEnabled || deathAbilityId.isBlank()) {
            return;
        }
        DragonAbilityDefinition ability = abilities.get(deathAbilityId);
        if (ability == null) {
            return;
        }
        triggerAbility(entity, encounter, definition, ability, System.currentTimeMillis());
    }

    private List<Player> selectTargets(EnderDragon entity, double range, int maximumTargets) {
        List<Player> targets = new ArrayList<>(entity.getWorld().getNearbyPlayers(
                entity.getLocation(),
                Math.max(1.0D, Math.min(participantRadius, range)),
                player -> validTarget(entity, player, range)
        ));
        Collections.shuffle(targets);
        return targets.stream().limit(Math.max(1, maximumTargets)).toList();
    }

    private boolean validTarget(EnderDragon entity, Player player, double range) {
        return player != null
                && player.isOnline()
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR
                && player.getWorld().equals(entity.getWorld())
                && player.getLocation().distanceSquared(entity.getLocation()) <= range * range;
    }

    private void damagePlayers(EnderDragon entity, Location center, double radius, double damage, int maximumTargets) {
        List<Player> targets = new ArrayList<>(center.getWorld().getNearbyPlayers(
                center,
                Math.max(0.5D, radius),
                player -> validTarget(entity, player, participantRadius)
        ));
        Collections.shuffle(targets);
        int limit = maximumTargets <= 0 ? targets.size() : Math.min(maximumTargets, targets.size());
        for (int index = 0; index < limit; index++) {
            targets.get(index).damage(damage, entity);
        }
    }

    private void showAbilityEffect(Location location, DragonAbilityDefinition ability, double radius) {
        int count = Math.max(4, Math.min(80, (int) Math.ceil(radius * 6.0D)));
        double offset = Math.max(0.1D, radius * 0.35D);
        location.getWorld().spawnParticle(ability.particle(), location, count, offset, offset, offset, 0.02D);
        if (!ability.sound().isBlank()) {
            location.getWorld().playSound(location, ability.sound(), 1.0F, 1.0F);
        }
    }

    private Optional<DragonPhaseDefinition> phaseFor(ActiveDragonEncounter encounter) {
        double healthPercent = encounter.maximumHealth() <= 0.0D
                ? 0.0D
                : encounter.currentHealth() * 100.0D / encounter.maximumHealth();
        return phases.values().stream()
                .sorted(Comparator.comparingDouble(DragonPhaseDefinition::minimumHealthPercent).reversed())
                .filter(phase -> healthPercent >= phase.minimumHealthPercent())
                .findFirst()
                .or(() -> phases.values().stream().min(Comparator.comparingDouble(DragonPhaseDefinition::minimumHealthPercent)));
    }

    private Optional<DragonPhaseDefinition> currentPhase(ActiveDragonEncounter encounter) {
        DragonPhaseDefinition current = phases.get(encounter.currentPhaseId());
        return current == null ? phaseFor(encounter) : Optional.of(current);
    }

    private long scaledDuration(long durationMillis, double multiplier) {
        return Math.max(250L, Math.round(durationMillis * Math.max(0.05D, multiplier)));
    }

    private void removeAbilityProjectiles(UUID encounterId) {
        String expected = encounterId.toString();
        for (World world : Bukkit.getWorlds()) {
            for (DragonFireball fireball : world.getEntitiesByClass(DragonFireball.class)) {
                String stored = fireball.getPersistentDataContainer().get(abilityEncounterKey, PersistentDataType.STRING);
                if (expected.equals(stored)) {
                    fireball.remove();
                }
            }
        }
    }

    private void sendNearby(Entity entity, String path, List<TextService.TextPlaceholder> placeholders) {
        entity.getWorld().getNearbyPlayers(entity.getLocation(), participantRadius)
                .forEach(player -> text.send(player, path, placeholders));
    }

    private void syncEntityHealth(EnderDragon entity, ActiveDragonEncounter encounter) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        if (Math.abs(attribute.getBaseValue() - physicalHealth) > 0.001D) {
            attribute.setBaseValue(physicalHealth);
        }
        double progress = encounter.maximumHealth() <= 0.0D ? 0.0D : encounter.currentHealth() / encounter.maximumHealth();
        entity.setHealth(Math.max(1.0D, Math.min(physicalHealth, physicalHealth * progress)));
    }

    private void updateBossBar(EnderDragon entity, ActiveDragonEncounter encounter) {
        if (!bossBarEnabled) {
            hideBossBar(entity.getUniqueId());
            return;
        }
        DragonDefinition definition = dragon(encounter.dragonId()).orElse(null);
        if (definition == null) {
            return;
        }
        BossBar bossBar = bossBars.computeIfAbsent(entity.getUniqueId(), ignored -> BossBar.bossBar(Component.empty(), 1.0F, bossBarColor, bossBarOverlay));
        bossBar.color(bossBarColor);
        bossBar.overlay(bossBarOverlay);
        bossBar.progress((float) Math.max(0.0D, Math.min(1.0D, encounter.currentHealth() / encounter.maximumHealth())));
        bossBar.name(text.deserialize(text.rawMessage("dragons.live-boss-bar-title"), livePlaceholders(encounter, definition, ownerName(encounter.ownerId()))));
        double radiusSquared = participantRadius * participantRadius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean visible = player.getWorld().equals(entity.getWorld())
                    && player.getLocation().distanceSquared(entity.getLocation()) <= radiusSquared;
            if (visible) {
                player.showBossBar(bossBar);
            } else {
                player.hideBossBar(bossBar);
            }
        }
    }

    private void hideBossBar(UUID entityId) {
        BossBar bossBar = bossBars.remove(entityId);
        if (bossBar == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }
    }

    private List<TextService.TextPlaceholder> livePlaceholders(ActiveDragonEncounter encounter, DragonDefinition definition, String ownerName) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(List.of(
                TextService.parsed("dragon", definition.displayName()),
                TextService.raw("dragon_id", definition.id()),
                TextService.raw("owner", ownerName),
                TextService.raw("health", text.formatNumber(encounter.currentHealth())),
                TextService.raw("max_health", text.formatNumber(encounter.maximumHealth())),
                TextService.raw("eyes", Integer.toString(encounter.eyes())),
                TextService.raw("time", Long.toString(remainingSeconds(encounter.expiresAtMillis())))
        ));
        placeholders.add(TextService.parsed(
                "phase",
                currentPhase(encounter).map(DragonPhaseDefinition::displayName).orElse(text.rawMessage("dragons.phase-none"))
        ));
        return placeholders;
    }

    private List<TextService.TextPlaceholder> phasePlaceholders(
            ActiveDragonEncounter encounter,
            DragonDefinition definition,
            DragonPhaseDefinition phase
    ) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(livePlaceholders(encounter, definition, ownerName(encounter.ownerId())));
        placeholders.add(TextService.raw("phase_id", phase.id()));
        return placeholders;
    }

    private List<TextService.TextPlaceholder> abilityPlaceholders(
            ActiveDragonEncounter encounter,
            DragonDefinition definition,
            DragonAbilityDefinition ability
    ) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(livePlaceholders(encounter, definition, ownerName(encounter.ownerId())));
        placeholders.add(TextService.raw("ability_id", ability.id()));
        placeholders.add(TextService.parsed("ability", ability.displayName()));
        return placeholders;
    }

    private void broadcast(String path, List<TextService.TextPlaceholder> placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            text.send(player, path, placeholders);
        }
    }

    private String ownerName(UUID ownerId) {
        SkyBlockProfile profile = profiles.profile(ownerId);
        if (profile != null && profile.playerName() != null && !profile.playerName().isBlank()) {
            return profile.playerName();
        }
        return ownerId.toString();
    }

    private long remainingSeconds(long expiresAtMillis) {
        return Math.max(0L, (expiresAtMillis - System.currentTimeMillis() + 999L) / 1000L);
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void cleanupStaleEntities() {
        Set<UUID> activeIds = new HashSet<>(activeEncounters.keySet());
        for (World world : Bukkit.getWorlds()) {
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                if (isLiveDragon(dragon) && !activeIds.contains(dragon.getUniqueId())) {
                    dragon.remove();
                }
            }
            for (DragonFireball fireball : world.getEntitiesByClass(DragonFireball.class)) {
                String encounterValue = fireball.getPersistentDataContainer().get(abilityEncounterKey, PersistentDataType.STRING);
                UUID encounterId = parseUuid(encounterValue).orElse(null);
                if (isAbilityProjectile(fireball) && (encounterId == null || !activeIds.contains(encounterId))) {
                    fireball.remove();
                }
            }
        }
    }

    private BossBar.Color parseBossBarColor(String value) {
        try {
            return BossBar.Color.valueOf(value == null ? "PURPLE" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.PURPLE;
        }
    }

    private BossBar.Overlay parseBossBarOverlay(String value) {
        try {
            return BossBar.Overlay.valueOf(value == null ? "PROGRESS" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private void loadCombat(ConfigurationSection section) {
        phases.clear();
        abilities.clear();
        combatEnabled = section == null || section.getBoolean("enabled", true);
        enforceConfiguredPhases = section == null || section.getBoolean("enforce-configured-phases", true);
        combatTickIntervalTicks = Math.max(1L, section == null ? 5L : section.getLong("tick-interval-ticks", 5L));
        List<String> configuredDefaults = section == null ? List.of() : normalizedList(section.getStringList("default-abilities"));
        defaultAbilityIds = configuredDefaults.isEmpty()
                ? List.of("RUSH", "FIREBALL", "LIGHTNING")
                : configuredDefaults;

        ConfigurationSection phaseSection = section == null ? null : section.getConfigurationSection("phases");
        if (phaseSection != null) {
            for (String id : phaseSection.getKeys(false)) {
                ConfigurationSection configured = phaseSection.getConfigurationSection(id);
                if (configured == null) {
                    continue;
                }
                String normalized = id.toUpperCase(Locale.ROOT);
                phases.put(normalized, new DragonPhaseDefinition(
                        normalized,
                        configured.getString("display-name", normalized),
                        Math.max(0.0D, Math.min(100.0D, configured.getDouble("minimum-health-percent", 0.0D))),
                        parseDragonPhase(configured.getString("native-phase", "CIRCLING"), EnderDragon.Phase.CIRCLING),
                        Math.max(0.0D, configured.getDouble("outgoing-damage-multiplier", 1.0D)),
                        Math.max(0.05D, configured.getDouble("cooldown-multiplier", 1.0D)),
                        configured.getBoolean("announce", false)
                ));
            }
        }
        if (phases.isEmpty()) {
            phases.put("NORMAL", new DragonPhaseDefinition(
                    "NORMAL", "<light_purple>Sky Assault</light_purple>", 50.0D,
                    EnderDragon.Phase.CIRCLING, 1.0D, 1.0D, false
            ));
            phases.put("ENRAGED", new DragonPhaseDefinition(
                    "ENRAGED", "<red>Enraged</red>", 0.0D,
                    EnderDragon.Phase.STRAFING, 1.5D, 0.67D, true
            ));
        }

        ConfigurationSection abilitySection = section == null ? null : section.getConfigurationSection("abilities");
        if (abilitySection != null) {
            for (String id : abilitySection.getKeys(false)) {
                ConfigurationSection configured = abilitySection.getConfigurationSection(id);
                if (configured == null) {
                    continue;
                }
                String normalized = id.toUpperCase(Locale.ROOT);
                DragonAbilityType type = parseAbilityType(configured.getString("type", normalized));
                if (type == null) {
                    plugin.getLogger().warning("Ignoring dragon ability " + normalized + " with unknown type.");
                    continue;
                }
                abilities.put(normalized, new DragonAbilityDefinition(
                        normalized,
                        configured.getString("display-name", normalized),
                        type,
                        secondsToMillis(configured.getDouble("initial-delay-seconds", 5.0D)),
                        secondsToMillis(configured.getDouble("cooldown-seconds", 15.0D)),
                        secondsToMillis(configured.getDouble("duration-seconds", 2.0D)),
                        Math.max(0.0D, configured.getDouble("damage", 8.0D)),
                        Math.max(0.5D, configured.getDouble("radius", 3.0D)),
                        Math.max(1.0D, configured.getDouble("range", participantRadius)),
                        Math.max(0.1D, configured.getDouble("speed", 1.0D)),
                        Math.max(0, configured.getInt("target-count", 1)),
                        Set.copyOf(normalizedList(configured.getStringList("phases"))),
                        parseDragonPhase(configured.getString("native-phase", "CIRCLING"), EnderDragon.Phase.CIRCLING),
                        parseParticle(configured.getString("particle", "DRAGON_BREATH")),
                        configured.getString("sound", ""),
                        configured.getBoolean("announce", false)
                ));
            }
        }
        if (abilities.isEmpty()) {
            loadDefaultAbilities();
        }
    }

    private void loadDefaultAbilities() {
        abilities.putIfAbsent("RUSH", new DragonAbilityDefinition(
                "RUSH", "<red>Rush</red>", DragonAbilityType.RUSH,
                8_000L, 18_000L, 4_000L, 12.0D, 3.0D, 80.0D, 2.2D, 1,
                Set.of(), EnderDragon.Phase.CHARGE_PLAYER, Particle.EXPLOSION,
                "entity.ender_dragon.growl", true
        ));
        abilities.putIfAbsent("FIREBALL", new DragonAbilityDefinition(
                "FIREBALL", "<gold>Fireball</gold>", DragonAbilityType.FIREBALL,
                4_000L, 12_000L, 2_000L, 10.0D, 4.0D, 90.0D, 1.2D, 1,
                Set.of(), EnderDragon.Phase.STRAFING, Particle.DRAGON_BREATH,
                "entity.ender_dragon.shoot", false
        ));
        abilities.putIfAbsent("LIGHTNING", new DragonAbilityDefinition(
                "LIGHTNING", "<yellow>Lightning</yellow>", DragonAbilityType.LIGHTNING,
                12_000L, 22_000L, 1_500L, 8.0D, 2.0D, 70.0D, 1.0D, 3,
                Set.of(), EnderDragon.Phase.CIRCLING, Particle.ELECTRIC_SPARK,
                "entity.lightning_bolt.thunder", true
        ));
    }

    private DragonCombatDefinition loadDragonCombat(ConfigurationSection dragonSection) {
        ConfigurationSection combat = dragonSection.getConfigurationSection("combat");
        List<String> configuredAbilities = combat == null ? List.of() : normalizedList(combat.getStringList("abilities"));
        return new DragonCombatDefinition(
                Math.max(0.0D, combat == null ? 1.0D : combat.getDouble("incoming-damage-multiplier", 1.0D)),
                Math.max(0.0D, combat == null ? 1.0D : combat.getDouble("outgoing-damage-multiplier", 1.0D)),
                Math.max(0.05D, combat == null ? 1.0D : combat.getDouble("cooldown-multiplier", 1.0D)),
                Math.max(0.05D, combat == null ? 1.0D : combat.getDouble("movement-speed-multiplier", 1.0D)),
                configuredAbilities.isEmpty() ? defaultAbilityIds : configuredAbilities,
                combat == null ? "" : string(combat.getString("death-ability", ""), "").toUpperCase(Locale.ROOT)
        );
    }

    private List<String> normalizedList(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private DragonAbilityType parseAbilityType(String value) {
        try {
            return DragonAbilityType.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private EnderDragon.Phase parseDragonPhase(String value, EnderDragon.Phase fallback) {
        try {
            return EnderDragon.Phase.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private Particle parseParticle(String value) {
        try {
            return Particle.valueOf(value == null ? "DRAGON_BREATH" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Particle.DRAGON_BREATH;
        }
    }

    private long secondsToMillis(double seconds) {
        return Math.max(250L, Math.round(Math.max(0.0D, seconds) * 1000.0D));
    }

    private void loadDragons() {
        dragons.clear();
        ConfigurationSection section = configService.dragons().getConfigurationSection("dragons");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection dragonSection = section.getConfigurationSection(id);
            if (dragonSection == null) {
                continue;
            }
            List<DragonRewardDefinition> rewards = new ArrayList<>();
            for (Map<?, ?> rawReward : dragonSection.getMapList("rewards")) {
                reward(rawReward).ifPresent(rewards::add);
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            dragons.put(normalized, new DragonDefinition(
                    normalized,
                    dragonSection.getString("display-name", normalized),
                    Math.max(0.0D, dragonSection.getDouble("spawn-weight", 1.0D)),
                    Math.max(1.0D, dragonSection.getDouble("health", 10_000_000.0D)),
                    Math.max(0.0D, dragonSection.getDouble("combat-xp", 500.0D)),
                    dragonSection.getString("fragment-item", "").toUpperCase(Locale.ROOT),
                    Math.max(0, dragonSection.getInt("base-fragments", 3)),
                    Math.max(0, dragonSection.getInt("fragments-per-eye", 2)),
                    Math.max(0, dragonSection.getInt("fragments-per-rank", 2)),
                    loadDragonCombat(dragonSection),
                    List.copyOf(rewards)
            ));
        }
    }

    private Optional<DragonRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        int minEyes = Math.max(0, integer(section.get("min-eyes"), 0));
        double minDamage = Math.max(0.0D, decimal(section.get("min-damage"), 0.0D));
        if (weight <= 0.0D) {
            return Optional.empty();
        }
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new DragonRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight, minEyes, minDamage));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new DragonRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight, minEyes, minDamage));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "ENDER_PEARL"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new DragonRewardDefinition("VANILLA", "", material, amount, 0.0D, weight, minEyes, minDamage));
    }

    private Optional<DragonDefinition> selectDragon() {
        double totalWeight = dragons.values().stream().mapToDouble(DragonDefinition::spawnWeight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (DragonDefinition dragon : definitions()) {
            cumulative += dragon.spawnWeight();
            if (selected <= cumulative) {
                return Optional.of(dragon);
            }
        }
        return definitions().stream().findFirst();
    }

    private Optional<DragonRewardDefinition> selectReward(DragonDefinition dragon, int eyes, double damage) {
        List<DragonRewardDefinition> eligible = dragon.rewards().stream()
                .filter(reward -> eyes >= reward.minEyes() && damage >= reward.minDamage())
                .toList();
        double totalWeight = eligible.stream().mapToDouble(DragonRewardDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (DragonRewardDefinition reward : eligible) {
            cumulative += reward.weight();
            if (selected <= cumulative) {
                return Optional.of(reward);
            }
        }
        return eligible.stream().findFirst();
    }

    private Optional<String> giveFragments(Player player, DragonDefinition dragon, int eyes, int rank) {
        if (dragon.fragmentItemId().isBlank()) {
            return Optional.empty();
        }
        CustomItemDefinition definition = customItems.definition(dragon.fragmentItemId()).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        int amount = dragon.baseFragments() + eyes * dragon.fragmentsPerEye() + Math.max(0, 4 - rank) * dragon.fragmentsPerRank();
        if (amount <= 0) {
            return Optional.empty();
        }
        ItemStack itemStack = customItems.createItem(definition);
        itemStack.setAmount(amount);
        giveItem(player, itemStack);
        return Optional.of("<yellow>" + amount + "x</yellow> " + definition.displayName());
    }

    private Optional<String> giveReward(Player player, DragonRewardDefinition reward) {
        if (reward.coinsReward()) {
            economy.addPurse(player, reward.coins());
            return Optional.of("<gold>" + text.formatNumber(reward.coins()) + " coins</gold>");
        }
        ItemStack itemStack;
        String display;
        if (reward.customItem()) {
            CustomItemDefinition definition = customItems.definition(reward.itemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            itemStack = customItems.createItem(definition);
            display = definition.displayName();
        } else {
            itemStack = new ItemStack(reward.material());
            display = readableMaterial(reward.material());
        }
        itemStack.setAmount(reward.amount());
        giveItem(player, itemStack);
        return Optional.of("<yellow>" + reward.amount() + "x</yellow> " + display);
    }

    private int damageRank(double damage) {
        if (damage >= damageForRankOne) {
            return 1;
        }
        if (damage >= damageForRankTwo) {
            return 2;
        }
        if (damage >= damageForRankThree) {
            return 3;
        }
        return 4;
    }

    private int countItem(Player player, String itemId) {
        int amount = 0;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (matches(itemStack, itemId)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private void consumeItem(Player player, String itemId, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (!matches(itemStack, itemId)) {
                continue;
            }
            int removed = Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - removed);
            remaining -= removed;
            if (itemStack.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private boolean matches(ItemStack itemStack, String itemId) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        return customItems.definition(itemStack)
                .map(definition -> definition.id().equalsIgnoreCase(itemId))
                .orElse(false);
    }

    private void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private List<TextService.TextPlaceholder> altarPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.raw("eyes", Integer.toString(profile.placedDragonEyes())),
                TextService.raw("eyes_required", Integer.toString(eyesRequired)),
                TextService.raw("eyes_needed", Integer.toString(Math.max(0, eyesRequired - profile.placedDragonEyes())))
        );
    }

    private List<TextService.TextPlaceholder> dragonPlaceholders(SkyBlockProfile profile, DragonDefinition dragon) {
        return List.of(
                TextService.raw("id", dragon.id()),
                TextService.parsed("dragon", dragon.displayName()),
                TextService.raw("health", text.formatNumber(dragon.health())),
                TextService.raw("combat_xp", text.formatNumber(dragon.combatXp())),
                TextService.raw("kills", Integer.toString(profile.dragonKills(dragon.id()))),
                TextService.raw("spawn_weight", text.formatNumber(dragon.spawnWeight()))
        );
    }

    private String readableMaterial(Material material) {
        String normalized = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double decimal(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(string(value, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
