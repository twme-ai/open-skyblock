package io.github.openskyblock.miningfiesta;

import io.github.openskyblock.calendar.CalendarService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.mayor.MayorService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MiningFiestaService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final MayorService mayors;
    private final CalendarService calendar;
    private final Set<Material> eligibleBlocks = new HashSet<>();
    private final Map<String, MiningFiestaBuffDefinition> buffs = new HashMap<>();
    private boolean requireMayorPerk = true;
    private String mayorModifier = "mining_fiesta_enabled";
    private double miningWisdomBonus = 75.0D;
    private int dropMultiplier = 2;
    private long blocksPerSkyBlockXp = 2500L;
    private double skyBlockXpPerMilestone = 5.0D;
    private String refinedMineralItemId = "REFINED_MINERAL";
    private String glossyGemstoneItemId = "GLOSSY_GEMSTONE";
    private double refinedMineralChance = 0.002D;
    private double glossyGemstoneChance = 0.0005D;
    private long firstEventDay = 0L;
    private long recurrenceDays = 124L;
    private long durationDays = 7L;
    private long saveIntervalBlocks = 25L;

    public MiningFiestaService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems, MayorService mayors, CalendarService calendar) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
        this.mayors = mayors;
        this.calendar = calendar;
    }

    public void reload() {
        requireMayorPerk = configService.miningFiesta().getBoolean("settings.require-mayor-perk", true);
        mayorModifier = configService.miningFiesta().getString("settings.mayor-modifier", "mining_fiesta_enabled").toLowerCase(Locale.ROOT).replace('-', '_');
        miningWisdomBonus = Math.max(0.0D, configService.miningFiesta().getDouble("settings.mining-wisdom-bonus", 75.0D));
        dropMultiplier = Math.max(1, configService.miningFiesta().getInt("settings.drop-multiplier", 2));
        blocksPerSkyBlockXp = Math.max(1L, configService.miningFiesta().getLong("settings.blocks-per-skyblock-xp", 2500L));
        skyBlockXpPerMilestone = Math.max(0.0D, configService.miningFiesta().getDouble("settings.skyblock-xp-per-milestone", 5.0D));
        refinedMineralItemId = configService.miningFiesta().getString("settings.refined-mineral-item", "REFINED_MINERAL").toUpperCase(Locale.ROOT);
        glossyGemstoneItemId = configService.miningFiesta().getString("settings.glossy-gemstone-item", "GLOSSY_GEMSTONE").toUpperCase(Locale.ROOT);
        refinedMineralChance = Math.max(0.0D, configService.miningFiesta().getDouble("settings.refined-mineral-chance", 0.002D));
        glossyGemstoneChance = Math.max(0.0D, configService.miningFiesta().getDouble("settings.glossy-gemstone-chance", 0.0005D));
        firstEventDay = Math.max(0L, configService.miningFiesta().getLong("settings.first-event-day", 0L));
        recurrenceDays = Math.max(1L, configService.miningFiesta().getLong("settings.recurrence-days", 124L));
        durationDays = Math.max(1L, configService.miningFiesta().getLong("settings.duration-days", 7L));
        saveIntervalBlocks = Math.max(1L, configService.miningFiesta().getLong("settings.save-interval-blocks", 25L));
        loadEligibleBlocks();
        loadBuffs();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.mining-fiesta", true);
    }

    public boolean eventActive() {
        return enabled() && mayorActive() && windowActive();
    }

    public List<String> buffIds() {
        return buffDefinitions().stream().map(MiningFiestaBuffDefinition::id).toList();
    }

    public List<MiningFiestaBuffDefinition> buffDefinitions() {
        return buffs.values().stream()
                .sorted(Comparator.comparing(MiningFiestaBuffDefinition::id))
                .toList();
    }

    public Optional<MiningFiestaBuffDefinition> buff(String rawBuffId) {
        if (rawBuffId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(buffs.get(rawBuffId.toUpperCase(Locale.ROOT)));
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mining-fiesta-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        purgeExpiredBuffs(profile);
        profiles.save(player);
        text.send(player, "commands.mining-fiesta-status", statusPlaceholders(profile));
    }

    public void sendDrops(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mining-fiesta-disabled");
            return;
        }
        text.send(player, "commands.mining-fiesta-drops", List.of(
                TextService.raw("blocks", Integer.toString(eligibleBlocks.size())),
                TextService.raw("multiplier", Integer.toString(dropMultiplier)),
                TextService.raw("refined_chance", percent(refinedMineralChance)),
                TextService.raw("glossy_chance", percent(glossyGemstoneChance))
        ));
    }

    public void sendBuffs(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mining-fiesta-disabled");
            return;
        }
        if (buffs.isEmpty()) {
            text.send(player, "commands.mining-fiesta-buffs-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        purgeExpiredBuffs(profile);
        text.send(player, "commands.mining-fiesta-buffs-header");
        for (MiningFiestaBuffDefinition buff : buffDefinitions()) {
            text.send(player, "commands.mining-fiesta-buff-line", buffPlaceholders(profile, buff));
        }
    }

    public boolean buyBuff(Player player, String rawBuffId) {
        if (!enabled()) {
            text.send(player, "commands.mining-fiesta-disabled");
            return false;
        }
        MiningFiestaBuffDefinition buff = buff(rawBuffId).orElse(null);
        if (buff == null) {
            text.send(player, "commands.mining-fiesta-unknown-buff", List.of(TextService.raw("buff", rawBuffId == null ? "" : rawBuffId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        List<String> missing = missingItems(player, buff.requiredItems());
        if (!missing.isEmpty()) {
            List<TextService.TextPlaceholder> placeholders = new ArrayList<>(buffPlaceholders(profile, buff));
            placeholders.add(TextService.parsed("missing", String.join("<gray>, </gray>", missing)));
            text.send(player, "commands.mining-fiesta-missing-items", placeholders);
            return false;
        }
        removeItems(player, buff.requiredItems());
        profile.setMiningFiestaBuff(buff.id(), System.currentTimeMillis() + buff.durationSeconds() * 1000L);
        profiles.save(player);
        text.send(player, "commands.mining-fiesta-buff-bought", buffPlaceholders(profile, buff));
        return true;
    }

    public void recordBlockBreak(Player player, Material material, Collection<ItemStack> naturalDrops) {
        if (!eventActive() || !eligibleBlocks.contains(material)) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        profile.addMiningFiestaBlocks(1L);
        giveExtraDrops(player, naturalDrops);
        maybeGiveSpecialDrop(player, profile, refinedMineralItemId, refinedMineralChance, true);
        maybeGiveSpecialDrop(player, profile, glossyGemstoneItemId, glossyGemstoneChance, false);
        if (profile.miningFiestaBlocks() % saveIntervalBlocks == 0L) {
            profiles.save(player);
        }
    }

    public Map<String, Double> activeStats(SkyBlockProfile profile) {
        Map<String, Double> stats = new HashMap<>();
        double mayorFortune = mayors.modifier("mining_fortune_bonus");
        if (mayorFortune > 0.0D) {
            stats.put("mining_fortune", mayorFortune);
        }
        if (eventActive()) {
            stats.put("mining_wisdom", stats.getOrDefault("mining_wisdom", 0.0D) + miningWisdomBonus);
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> active : profile.miningFiestaBuffs().entrySet()) {
            if (active.getValue() <= now) {
                continue;
            }
            MiningFiestaBuffDefinition buff = buffs.get(active.getKey());
            if (buff == null) {
                continue;
            }
            for (Map.Entry<String, Double> stat : buff.stats().entrySet()) {
                stats.put(stat.getKey(), stats.getOrDefault(stat.getKey(), 0.0D) + stat.getValue());
            }
        }
        return stats;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        if (!enabled()) {
            return 0.0D;
        }
        long milestones = profile.miningFiestaBlocks() / blocksPerSkyBlockXp;
        return milestones * skyBlockXpPerMilestone;
    }

    private void giveExtraDrops(Player player, Collection<ItemStack> naturalDrops) {
        int extraCopies = Math.max(0, dropMultiplier - 1);
        if (extraCopies <= 0 || naturalDrops == null || naturalDrops.isEmpty()) {
            return;
        }
        for (int copy = 0; copy < extraCopies; copy++) {
            for (ItemStack drop : naturalDrops) {
                if (drop == null || drop.getType().isAir()) {
                    continue;
                }
                giveItem(player, drop.clone());
            }
        }
    }

    private void maybeGiveSpecialDrop(Player player, SkyBlockProfile profile, String itemId, double chance, boolean refined) {
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        CustomItemDefinition definition = customItems.definition(itemId).orElse(null);
        if (definition == null) {
            return;
        }
        giveCustomItem(player, definition, 1);
        long total;
        if (refined) {
            profile.addMiningFiestaRefinedMinerals(1L);
            total = profile.miningFiestaRefinedMinerals();
        } else {
            profile.addMiningFiestaGlossyGemstones(1L);
            total = profile.miningFiestaGlossyGemstones();
        }
        text.send(player, "commands.mining-fiesta-drop-found", List.of(
                TextService.raw("amount", "1"),
                TextService.parsed("item", definition.displayName()),
                TextService.raw("total", text.formatNumber(total))
        ));
    }

    private boolean mayorActive() {
        return !requireMayorPerk || mayors.modifier(mayorModifier) > 0.0D;
    }

    private boolean windowActive() {
        return windowOffset() < durationDays;
    }

    private long windowOffset() {
        long day = calendar.currentDate().absoluteDay();
        return Math.floorMod(day - firstEventDay, recurrenceDays);
    }

    private String windowStatus() {
        long offset = windowOffset();
        if (offset < durationDays) {
            long remaining = durationDays - offset;
            return "active, " + remaining + " SB day(s) remaining";
        }
        return "starts in " + (recurrenceDays - offset) + " SB day(s)";
    }

    private void purgeExpiredBuffs(SkyBlockProfile profile) {
        long now = System.currentTimeMillis();
        profile.miningFiestaBuffs().entrySet().removeIf(entry -> entry.getValue() <= now || !buffs.containsKey(entry.getKey()));
    }

    private List<String> missingItems(Player player, Map<String, Integer> costs) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            int held = countCustomItem(player, entry.getKey());
            if (held < entry.getValue()) {
                String name = customItems.definition(entry.getKey()).map(CustomItemDefinition::displayName).orElse(entry.getKey());
                missing.add("<yellow>" + (entry.getValue() - held) + "x</yellow> " + name);
            }
        }
        return missing;
    }

    private int countCustomItem(Player player, String itemId) {
        int amount = 0;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (matchesCustomItem(itemStack, itemId)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private void removeItems(Player player, Map<String, Integer> costs) {
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
                ItemStack itemStack = player.getInventory().getItem(slot);
                if (!matchesCustomItem(itemStack, entry.getKey())) {
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
    }

    private boolean matchesCustomItem(ItemStack itemStack, String itemId) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        return customItems.definition(itemStack)
                .map(CustomItemDefinition::id)
                .map(id -> id.equalsIgnoreCase(itemId))
                .orElse(false);
    }

    private void giveCustomItem(Player player, CustomItemDefinition definition, int amount) {
        int remaining = Math.max(1, amount);
        int maxStack = Math.max(1, definition.material().getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack itemStack = customItems.createItem(definition);
            itemStack.setAmount(stackAmount);
            giveItem(player, itemStack);
            remaining -= stackAmount;
        }
    }

    private void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private List<TextService.TextPlaceholder> statusPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.parsed("active", eventActive() ? "<green>Active</green>" : "<red>Inactive</red>"),
                TextService.parsed("mayor", mayorActive() ? "<green>Enabled</green>" : "<red>Missing</red>"),
                TextService.raw("window", windowStatus()),
                TextService.raw("blocks", text.formatNumber(profile.miningFiestaBlocks())),
                TextService.raw("refined", text.formatNumber(profile.miningFiestaRefinedMinerals())),
                TextService.raw("glossy", text.formatNumber(profile.miningFiestaGlossyGemstones())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        );
    }

    private List<TextService.TextPlaceholder> buffPlaceholders(SkyBlockProfile profile, MiningFiestaBuffDefinition buff) {
        long expiresAt = profile.miningFiestaBuffExpiresAt(buff.id());
        String status = expiresAt > System.currentTimeMillis() ? "<green>Active " + remaining(expiresAt) + "</green>" : "<yellow>Available</yellow>";
        return List.of(
                TextService.raw("id", buff.id()),
                TextService.raw("buff_id", buff.id()),
                TextService.parsed("buff", buff.displayName()),
                TextService.parsed("status", status),
                TextService.raw("duration", duration(buff.durationSeconds())),
                TextService.parsed("cost", itemCosts(buff.requiredItems())),
                TextService.parsed("stats", statList(buff.stats()))
        );
    }

    private String itemCosts(Map<String, Integer> costs) {
        if (costs.isEmpty()) {
            return "<gray>none</gray>";
        }
        return String.join("<gray>, </gray>", costs.entrySet().stream()
                .map(entry -> "<yellow>" + entry.getValue() + "x</yellow> " + customItems.definition(entry.getKey()).map(CustomItemDefinition::displayName).orElse(entry.getKey()))
                .toList());
    }

    private String statList(Map<String, Double> stats) {
        if (stats.isEmpty()) {
            return "<gray>none</gray>";
        }
        return String.join("<gray>, </gray>", stats.entrySet().stream()
                .map(entry -> "<green>+" + text.formatNumber(entry.getValue()) + "</green> <gray>" + text.statName(entry.getKey()) + "</gray>")
                .toList());
    }

    private String percent(double chance) {
        return text.formatNumber(chance * 100.0D) + "%";
    }

    private String remaining(long expiresAtMillis) {
        long seconds = Math.max(0L, (expiresAtMillis - System.currentTimeMillis() + 999L) / 1000L);
        return duration(seconds);
    }

    private String duration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        long remainingSeconds = seconds % 60L;
        return minutes > 0L ? minutes + "m " + remainingSeconds + "s" : remainingSeconds + "s";
    }

    private void loadEligibleBlocks() {
        eligibleBlocks.clear();
        for (String raw : configService.miningFiesta().getStringList("eligible-blocks")) {
            Material material = Material.matchMaterial(raw);
            if (material != null && !material.isAir()) {
                eligibleBlocks.add(material);
            }
        }
    }

    private void loadBuffs() {
        buffs.clear();
        ConfigurationSection section = configService.miningFiesta().getConfigurationSection("buffs");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection buff = section.getConfigurationSection(id);
            if (buff == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            buffs.put(normalized, new MiningFiestaBuffDefinition(
                    normalized,
                    buff.getString("display-name", normalized),
                    Math.max(1L, buff.getLong("duration-seconds", 3600L)),
                    readItemCosts(buff.getConfigurationSection("required-items")),
                    readStats(buff.getConfigurationSection("stats"))
            ));
        }
    }

    private Map<String, Integer> readItemCosts(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> costs = new HashMap<>();
        for (String id : section.getKeys(false)) {
            int amount = Math.max(1, section.getInt(id, 1));
            costs.put(id.toUpperCase(Locale.ROOT), amount);
        }
        return Map.copyOf(costs);
    }

    private Map<String, Double> readStats(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> stats = new HashMap<>();
        for (String stat : section.getKeys(false)) {
            stats.put(StatSnapshot.normalize(stat), section.getDouble(stat, 0.0D));
        }
        return Map.copyOf(stats);
    }
}
