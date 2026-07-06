package io.github.openskyblock.service;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.PlacedMinion;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class MinionService {
    private static final long MILLIS_PER_TICK = 50L;

    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CollectionService collections;
    private final Map<String, MinionDefinition> definitions = new HashMap<>();

    public MinionService(ConfigService configService, TextService text, ProfileManager profiles, CollectionService collections) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.collections = collections;
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
                    minionSection.getLong("storage-size", 64L)
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

    public void addMinion(Player player, MinionDefinition definition) {
        SkyBlockProfile profile = profiles.profile(player);
        profile.minions().add(new PlacedMinion(definition.id(), 0L, System.currentTimeMillis()));
        text.send(player, "commands.minion-added", List.of(TextService.parsed("minion_name", definition.displayName())));
    }

    public long claim(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            return 0L;
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null || placedMinion.generatedAmount() <= 0L) {
            return 0L;
        }
        long generated = placedMinion.generatedAmount();
        placedMinion.generatedAmount(0L);
        collections.addProgress(player, definition.generatedCollection(), generated);
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

    public void tickOnlineMinions(Collection<? extends Player> players) {
        if (!configService.main().getBoolean("features.minions", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : players) {
            SkyBlockProfile profile = profiles.profile(player);
            for (PlacedMinion placedMinion : profile.minions()) {
                MinionDefinition definition = definition(placedMinion.id()).orElse(null);
                if (definition == null) {
                    continue;
                }
                long intervalMillis = Math.max(1L, definition.intervalTicks()) * MILLIS_PER_TICK;
                long elapsed = now - placedMinion.lastActionMillis();
                long cycles = elapsed / intervalMillis;
                if (cycles <= 0L) {
                    continue;
                }
                long capacity = Math.max(0L, definition.storageSize() - placedMinion.generatedAmount());
                long generated = Math.min(capacity, cycles * Math.max(1L, definition.generatedAmount()));
                if (generated > 0L) {
                    placedMinion.generatedAmount(placedMinion.generatedAmount() + generated);
                }
                placedMinion.lastActionMillis(placedMinion.lastActionMillis() + cycles * intervalMillis);
            }
        }
    }
}
