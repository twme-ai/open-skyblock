package io.github.openskyblock.mob;

import io.github.openskyblock.bestiary.BestiaryService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.service.ActionReward;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.stats.StatService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobService {
    private static final String DEFAULT_NAME_FORMAT = "<gray>[Lv<level>]</gray> <mob> <red><health></red><dark_gray>/</dark_gray><red><max_health> HP</red>";

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final CustomItemService customItems;
    private final SkillService skills;
    private final StatService stats;
    private final BestiaryService bestiary;
    private final NamespacedKey mobIdKey;
    private final Map<String, SkyBlockMobDefinition> definitions = new HashMap<>();
    private String nameFormat = DEFAULT_NAME_FORMAT;
    private int spawnLimitPerCommand = 25;
    private boolean rareDropAnnouncements = true;
    private double rareDropAnnounceThreshold = 5.0D;
    private double rareDropBroadcastThreshold = 1.0D;
    private Sound rareDropSound = Sound.ENTITY_PLAYER_LEVELUP;
    private float rareDropSoundVolume = 1.0F;
    private float rareDropSoundPitch = 1.2F;

    public MobService(JavaPlugin plugin, ConfigService configService, TextService text, CustomItemService customItems, SkillService skills, StatService stats, BestiaryService bestiary) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.customItems = customItems;
        this.skills = skills;
        this.stats = stats;
        this.bestiary = bestiary;
        this.mobIdKey = new NamespacedKey(plugin, "mob_id");
    }

    public void reload() {
        definitions.clear();
        String configuredNameFormat = configService.mobs().getString("settings.name-format", DEFAULT_NAME_FORMAT);
        this.nameFormat = configuredNameFormat == null || configuredNameFormat.isBlank() ? DEFAULT_NAME_FORMAT : configuredNameFormat;
        this.spawnLimitPerCommand = Math.max(1, Math.min(100, configService.mobs().getInt("settings.spawn-limit-per-command", 25)));
        this.rareDropAnnouncements = configService.mobs().getBoolean("settings.rare-drop-announcements", true);
        this.rareDropAnnounceThreshold = Math.max(0.0D, Math.min(100.0D, configService.mobs().getDouble("settings.rare-drop-announce-threshold", 5.0D)));
        this.rareDropBroadcastThreshold = Math.max(0.0D, Math.min(100.0D, configService.mobs().getDouble("settings.rare-drop-broadcast-threshold", 1.0D)));
        this.rareDropSound = parseSound(configService.mobs().getString("settings.rare-drop-sound", "ENTITY_PLAYER_LEVELUP"));
        this.rareDropSoundVolume = (float) Math.max(0.0D, configService.mobs().getDouble("settings.rare-drop-sound-volume", 1.0D));
        this.rareDropSoundPitch = (float) Math.max(0.1D, configService.mobs().getDouble("settings.rare-drop-sound-pitch", 1.2D));
        ConfigurationSection section = configService.mobs().getConfigurationSection("mobs");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection mobSection = section.getConfigurationSection(id);
            if (mobSection == null) {
                continue;
            }
            EntityType entityType = parseEntityType(mobSection.getString("entity-type", "ZOMBIE"));
            if (entityType == null || !entityType.isAlive() || !entityType.isSpawnable()) {
                plugin.getLogger().warning("Skipping invalid custom mob entity type in mobs.yml: " + id);
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            String collectionId = mobSection.getString("collection", "");
            if (collectionId != null && !collectionId.isBlank()) {
                collectionId = collectionId.toUpperCase(Locale.ROOT);
            }
            SkillType skillType = SkillType.fromKey(mobSection.getString("skill", "COMBAT")).orElse(SkillType.COMBAT);
            double skillXp = mobSection.getDouble("xp", mobSection.getDouble("combat-xp", 0.0D));
            definitions.put(normalized, new SkyBlockMobDefinition(
                    normalized,
                    entityType,
                    mobSection.getString("display-name", id),
                    Math.max(1, mobSection.getInt("level", 1)),
                    Math.max(1.0D, mobSection.getDouble("health", 100.0D)),
                    Math.max(0.0D, mobSection.getDouble("damage", 10.0D)),
                    Math.max(0.0D, mobSection.getDouble("defense", 0.0D)),
                    skillType,
                    Math.max(0.0D, skillXp),
                    Math.max(0.0D, mobSection.getDouble("coins", 0.0D)),
                    collectionId,
                    Math.max(0L, mobSection.getLong("collection-amount", 0L)),
                    mobSection.getBoolean("vanilla-drops", false),
                    readDrops(mobSection.getConfigurationSection("drops"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.custom-mobs", true);
    }

    public int spawnLimitPerCommand() {
        return spawnLimitPerCommand;
    }

    public List<SkyBlockMobDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparingInt(SkyBlockMobDefinition::level).thenComparing(SkyBlockMobDefinition::id))
                .toList();
    }

    public Optional<SkyBlockMobDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<SkyBlockMobDefinition> definition(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        String id = entity.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        return definition(id);
    }

    public int spawn(Player player, SkyBlockMobDefinition definition, int requestedAmount) {
        if (!enabled()) {
            text.send(player, "commands.mob-disabled");
            return 0;
        }
        int amount = Math.max(1, Math.min(requestedAmount, spawnLimitPerCommand));
        Location location = player.getLocation();
        for (int count = 0; count < amount; count++) {
            spawn(location, definition);
        }
        return amount;
    }

    public LivingEntity spawn(Location location, SkyBlockMobDefinition definition) {
        Entity entity = location.getWorld().spawnEntity(location, definition.entityType());
        if (!(entity instanceof LivingEntity livingEntity)) {
            entity.remove();
            throw new IllegalStateException("Configured custom mob is not a living entity: " + definition.id());
        }
        livingEntity.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, definition.id());
        AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(definition.health());
        }
        livingEntity.setHealth(Math.min(definition.health(), livingEntity.getMaxHealth()));
        livingEntity.setRemoveWhenFarAway(false);
        livingEntity.setPersistent(false);
        refreshName(livingEntity);
        return livingEntity;
    }

    public void refreshName(Entity entity) {
        if (!entity.isValid() || !(entity instanceof LivingEntity livingEntity)) {
            return;
        }
        SkyBlockMobDefinition definition = definition(entity).orElse(null);
        if (definition == null) {
            return;
        }
        livingEntity.customName(text.deserialize(nameFormat, List.of(
                TextService.raw("level", Integer.toString(definition.level())),
                TextService.parsed("mob", definition.displayName()),
                TextService.raw("health", text.formatNumber(Math.max(0.0D, livingEntity.getHealth()))),
                TextService.raw("max_health", text.formatNumber(definition.health()))
        )));
        livingEntity.setCustomNameVisible(true);
    }

    public void refreshNameNextTick(Entity entity) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshName(entity));
    }

    public void handleDeath(EntityDeathEvent event) {
        SkyBlockMobDefinition definition = definition(event.getEntity()).orElse(null);
        if (definition == null) {
            return;
        }
        if (!definition.vanillaDrops()) {
            event.getDrops().clear();
        }
        event.setDroppedExp(0);
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        skills.grantActionReward(killer, new ActionReward(
                definition.skillType(),
                definition.skillXp(),
                definition.collectionId(),
                definition.collectionAmount(),
                definition.coins()
        ));
        bestiary.recordKill(killer, definition.id());
        for (ItemStack itemStack : rollDrops(killer, definition)) {
            event.getDrops().add(itemStack);
        }
    }

    public List<ItemStack> rollDrops(Player player, SkyBlockMobDefinition definition) {
        List<ItemStack> results = new ArrayList<>();
        double magicFind = Math.max(0.0D, stats.snapshot(player).stat("magic_find"));
        for (MobDropDefinition drop : definition.drops()) {
            double chance = drop.chance();
            if (drop.magicFind()) {
                chance *= 1.0D + magicFind / 100.0D;
            }
            if (chance < 100.0D && ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
                continue;
            }
            ItemStack itemStack = createDrop(drop).orElse(null);
            if (itemStack == null) {
                continue;
            }
            itemStack.setAmount(randomAmount(drop));
            results.add(itemStack);
            announceDrop(player, definition, drop, itemStack, Math.min(100.0D, chance));
        }
        return results;
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mob-disabled");
            return;
        }
        List<SkyBlockMobDefinition> mobs = definitions();
        if (mobs.isEmpty()) {
            text.send(player, "commands.mob-empty");
            return;
        }
        text.send(player, "commands.mob-list-header");
        for (SkyBlockMobDefinition definition : mobs) {
            text.send(player, "commands.mob-list-line", List.of(
                    TextService.raw("id", definition.id()),
                    TextService.parsed("mob", definition.displayName()),
                    TextService.raw("level", Integer.toString(definition.level())),
                    TextService.raw("health", text.formatNumber(definition.health())),
                    TextService.raw("damage", text.formatNumber(definition.damage())),
                    TextService.raw("drops", Integer.toString(definition.drops().size()))
            ));
        }
    }

    private Optional<ItemStack> createDrop(MobDropDefinition drop) {
        if (drop.type() == MobDropType.CUSTOM_ITEM) {
            CustomItemDefinition definition = customItems.definition(drop.customItemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            return Optional.of(customItems.createItem(definition));
        }
        if (drop.material() == null || !drop.material().isItem() || drop.material().isAir()) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(drop.material()));
    }

    private int randomAmount(MobDropDefinition drop) {
        int min = Math.max(1, drop.minAmount());
        int max = Math.max(min, drop.maxAmount());
        if (min == max) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void announceDrop(Player player, SkyBlockMobDefinition mob, MobDropDefinition drop, ItemStack itemStack, double effectiveChance) {
        if (!rareDropAnnouncements || !drop.announce()) {
            return;
        }
        List<TextService.TextPlaceholder> placeholders = List.of(
                TextService.raw("player", player.getName()),
                TextService.parsed("mob", mob.displayName()),
                TextService.parsed("item", itemName(drop, itemStack)),
                TextService.raw("chance", text.formatNumber(effectiveChance))
        );
        text.send(player, "mobs.rare-drop", placeholders);
        if (rareDropSound != null) {
            player.playSound(player.getLocation(), rareDropSound, rareDropSoundVolume, rareDropSoundPitch);
        }
        if (!drop.broadcast()) {
            return;
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            text.send(online, "mobs.rare-drop-broadcast", placeholders);
        }
    }

    private String itemName(MobDropDefinition drop, ItemStack itemStack) {
        String name = drop.type() == MobDropType.CUSTOM_ITEM
                ? customItems.definition(drop.customItemId()).map(CustomItemDefinition::displayName).orElse(drop.customItemId())
                : "<white>" + readableMaterial(drop.material()) + "</white>";
        if (itemStack.getAmount() <= 1) {
            return name;
        }
        return "<white>" + itemStack.getAmount() + "x</white> " + name;
    }

    private String readableMaterial(Material material) {
        if (material == null || material.isAir()) {
            return "Unknown Item";
        }
        String readable = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = readable.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.isEmpty() ? material.name() : builder.toString();
    }

    private List<MobDropDefinition> readDrops(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<MobDropDefinition> drops = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection dropSection = section.getConfigurationSection(id);
            if (dropSection == null) {
                continue;
            }
            MobDropType type = MobDropType.parse(dropSection.getString("type", "VANILLA"));
            Material material = Material.matchMaterial(dropSection.getString("material", id));
            String customItemId = dropSection.getString("custom-item", id);
            customItemId = customItemId == null || customItemId.isBlank() ? id : customItemId.toUpperCase(Locale.ROOT);
            int minAmount = Math.max(1, dropSection.getInt("min-amount", dropSection.getInt("amount", 1)));
            int maxAmount = Math.max(minAmount, dropSection.getInt("max-amount", minAmount));
            double chance = Math.max(0.0D, Math.min(100.0D, dropSection.getDouble("chance", 100.0D)));
            boolean announce = dropSection.getBoolean("announce", chance > 0.0D && chance <= rareDropAnnounceThreshold);
            boolean broadcast = dropSection.getBoolean("broadcast", chance > 0.0D && chance <= rareDropBroadcastThreshold);
            if (broadcast) {
                announce = true;
            }
            drops.add(new MobDropDefinition(
                    id.toUpperCase(Locale.ROOT),
                    type,
                    material == null ? Material.AIR : material,
                    customItemId,
                    chance,
                    minAmount,
                    maxAmount,
                    dropSection.getBoolean("magic-find", false),
                    announce,
                    broadcast
            ));
        }
        return List.copyOf(drops);
    }

    private Sound parseSound(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("none")) {
            return null;
        }
        try {
            return Sound.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Sound.ENTITY_PLAYER_LEVELUP;
        }
    }

    private EntityType parseEntityType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
