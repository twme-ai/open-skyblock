package io.github.openskyblock.service;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.PlacedMinion;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.upgrade.UpgradeService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinionService {
    private static final long MILLIS_PER_TICK = 50L;

    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CollectionService collections;
    private final UpgradeService upgrades;
    private final NamespacedKey minionIdKey;
    private final Map<String, MinionDefinition> definitions = new HashMap<>();

    public MinionService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, CollectionService collections, UpgradeService upgrades) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.collections = collections;
        this.upgrades = upgrades;
        this.minionIdKey = new NamespacedKey(plugin, "minion_id");
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.minions().getConfigurationSection("minions");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection minionSection = section.getConfigurationSection(id);
            if (minionSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(minionSection.getString("material", "STONE"));
            definitions.put(id.toUpperCase(Locale.ROOT), new MinionDefinition(
                    id.toUpperCase(Locale.ROOT),
                    minionSection.getString("display-name", id),
                    material == null ? Material.STONE : material,
                    minionSection.getString("generated-collection", "").toUpperCase(Locale.ROOT),
                    minionSection.getLong("generated-amount", 1L),
                    minionSection.getLong("interval-ticks", 400L),
                    minionSection.getLong("storage-size", 64L),
                    minionSection.getInt("required-collection-tier", 0)
            ));
        }
    }

    public Optional<MinionDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<MinionDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(MinionDefinition::id))
                .toList();
    }

    public Optional<MinionDefinition> definition(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = itemStack.getItemMeta().getPersistentDataContainer().get(minionIdKey, PersistentDataType.STRING);
        return definition(id);
    }

    public ItemStack createMinionItem(MinionDefinition definition) {
        ItemStack itemStack = new ItemStack(definition.material());
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text.deserialize(definition.displayName()));
        meta.lore(minionItemLore(definition));
        meta.getPersistentDataContainer().set(minionIdKey, PersistentDataType.STRING, definition.id());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public void addMinion(Player player, MinionDefinition definition) {
        SkyBlockProfile profile = profiles.profile(player);
        if (!hasMinionCapacity(player, profile)) {
            return;
        }
        profile.minions().add(new PlacedMinion(definition.id(), 0L, System.currentTimeMillis()));
        text.send(player, "commands.minion-added", List.of(TextService.parsed("minion_name", definition.displayName())));
    }

    public boolean placeMinion(Player player, MinionDefinition definition, Location location) {
        SkyBlockProfile profile = profiles.profile(player);
        if (!hasMinionCapacity(player, profile)) {
            return false;
        }
        if (find(location).isPresent()) {
            text.send(player, "commands.minion-location-used");
            return false;
        }
        PlacedMinion placedMinion = new PlacedMinion(definition.id(), 0L, System.currentTimeMillis());
        placedMinion.location(location);
        profile.minions().add(placedMinion);
        location.getBlock().setType(definition.material(), false);
        text.send(player, "commands.minion-placed", List.of(TextService.parsed("minion_name", definition.displayName())));
        return true;
    }

    public Optional<MinionPlacement> find(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            for (int slot = 0; slot < profile.minions().size(); slot++) {
                PlacedMinion placedMinion = profile.minions().get(slot);
                if (!placedMinion.matches(location)) {
                    continue;
                }
                MinionDefinition definition = definition(placedMinion.id()).orElse(null);
                if (definition == null) {
                    continue;
                }
                updateMinion(placedMinion, definition, System.currentTimeMillis());
                return Optional.of(new MinionPlacement(profile, slot, placedMinion, definition));
            }
        }
        return Optional.empty();
    }

    public Optional<MinionPlacement> placement(UUID owner, int slot) {
        SkyBlockProfile profile = profiles.profile(owner);
        if (profile == null || slot < 0 || slot >= profile.minions().size()) {
            return Optional.empty();
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        updateMinion(placedMinion, definition, System.currentTimeMillis());
        return Optional.of(new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public long claim(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            return 0L;
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            return 0L;
        }
        return claim(player, new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public long claim(Player player, MinionPlacement placement) {
        updateMinion(placement.placedMinion(), placement.definition(), System.currentTimeMillis());
        if (placement.placedMinion().generatedAmount() <= 0L) {
            return 0L;
        }
        PlacedMinion placedMinion = placement.placedMinion();
        MinionDefinition definition = placement.definition();
        long generated = placedMinion.generatedAmount();
        placedMinion.generatedAmount(0L);
        collections.addProgress(player, definition.generatedCollection(), generated);
        addGeneratedItems(player, definition, generated);
        return generated;
    }

    public long claimAll(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        long claimed = 0L;
        for (int slot = 0; slot < profile.minions().size(); slot++) {
            claimed += claim(player, slot);
        }
        return claimed;
    }

    public boolean pickup(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        return pickup(player, new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public boolean pickup(Player player, MinionPlacement placement) {
        claim(player, placement);
        PlacedMinion placedMinion = placement.profile().minions().remove(placement.slot());
        MinionDefinition definition = placement.definition();
        if (placedMinion.hasLocation()) {
            Block block = Bukkit.getWorld(placedMinion.worldName()) == null
                    ? null
                    : Bukkit.getWorld(placedMinion.worldName()).getBlockAt(placedMinion.x(), placedMinion.y(), placedMinion.z());
            if (block != null) {
                block.setType(Material.AIR, false);
            }
        }
        giveOrDrop(player, createMinionItem(definition));
        text.send(player, "commands.minion-picked-up", List.of(TextService.parsed("minion_name", definition.displayName())));
        return true;
    }

    public void tickLoadedMinions() {
        if (!configService.main().getBoolean("features.minions", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            tickProfile(profile, now);
        }
    }

    public String locationLabel(PlacedMinion placedMinion) {
        if (!placedMinion.hasLocation()) {
            return text.rawMessage("minions.location-missing");
        }
        return text.rawMessage("minions.location-present")
                .replace("<world>", placedMinion.worldName())
                .replace("<x>", Integer.toString(placedMinion.x()))
                .replace("<y>", Integer.toString(placedMinion.y()))
                .replace("<z>", Integer.toString(placedMinion.z()));
    }

    public String collectionDisplayName(MinionDefinition definition) {
        return collections.definition(definition.generatedCollection())
                .map(CollectionDefinition::displayName)
                .orElse("<white>" + definition.generatedCollection() + "</white>");
    }

    private void tickProfile(SkyBlockProfile profile, long now) {
        for (PlacedMinion placedMinion : profile.minions()) {
            definition(placedMinion.id()).ifPresent(definition -> updateMinion(placedMinion, definition, now));
        }
    }

    private void updateMinion(PlacedMinion placedMinion, MinionDefinition definition, long now) {
        long intervalMillis = Math.max(1L, definition.intervalTicks()) * MILLIS_PER_TICK;
        long elapsed = now - placedMinion.lastActionMillis();
        long cycles = elapsed / intervalMillis;
        if (cycles <= 0L) {
            return;
        }
        long capacity = Math.max(0L, definition.storageSize() - placedMinion.generatedAmount());
        long generated = Math.min(capacity, cycles * Math.max(1L, definition.generatedAmount()));
        if (generated > 0L) {
            placedMinion.generatedAmount(placedMinion.generatedAmount() + generated);
        }
        placedMinion.lastActionMillis(placedMinion.lastActionMillis() + cycles * intervalMillis);
    }

    private boolean hasMinionCapacity(Player player, SkyBlockProfile profile) {
        int limit = Math.max(1, configService.main().getInt("minions.max-per-profile", 5) + upgrades.capacityBonus(profile, "minion_slots"));
        if (profile.minions().size() < limit) {
            return true;
        }
        text.send(player, "commands.minion-limit", List.of(TextService.raw("limit", Integer.toString(limit))));
        return false;
    }

    private List<Component> minionItemLore(MinionDefinition definition) {
        List<Component> lore = new ArrayList<>();
        List<TextService.TextPlaceholder> placeholders = minionPlaceholders(definition, 0L);
        for (String line : configService.messages().getStringList("minions.item-lore")) {
            lore.add(text.deserialize(line, placeholders));
        }
        lore.add(Component.empty());
        lore.add(text.message("minions.item-rarity-line"));
        return lore;
    }

    public List<TextService.TextPlaceholder> minionPlaceholders(MinionDefinition definition, long generated) {
        return List.of(
                TextService.parsed("minion_name", definition.displayName()),
                TextService.parsed("collection", collectionDisplayName(definition)),
                TextService.raw("generated", text.formatNumber(generated)),
                TextService.raw("storage", text.formatNumber(definition.storageSize())),
                TextService.raw("interval_seconds", text.formatNumber(definition.intervalTicks() / 20.0D))
        );
    }

    private void addGeneratedItems(Player player, MinionDefinition definition, long amount) {
        Material material = collections.definition(definition.generatedCollection())
                .map(CollectionDefinition::material)
                .orElse(definition.material());
        long remaining = amount;
        int stackSize = Math.max(1, material.getMaxStackSize());
        while (remaining > 0L) {
            int next = (int) Math.min(stackSize, remaining);
            giveOrDrop(player, new ItemStack(material, next));
            remaining -= next;
        }
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
