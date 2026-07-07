package io.github.openskyblock.quest;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CollectionDefinition;
import io.github.openskyblock.service.SkillDefinition;
import io.github.openskyblock.service.SkillType;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class QuestLogService {
    private final OpenSkyBlockPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final Map<String, QuestDefinition> definitions = new HashMap<>();

    public QuestLogService(OpenSkyBlockPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.quests().getConfigurationSection("quests");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection questSection = section.getConfigurationSection(id);
            if (questSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            definitions.put(normalized, new QuestDefinition(
                    normalized,
                    questSection.getString("display-name", id),
                    questSection.getString("category", "General"),
                    questSection.getString("material", "BOOK"),
                    questSection.getStringList("description"),
                    QuestObjectiveType.parse(questSection.getString("objective.type")),
                    questSection.getString("objective.target", ""),
                    Math.max(1.0D, questSection.getDouble("objective.required", 1.0D)),
                    questSection.getInt("sort", definitions.size())
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.quest-log", true);
    }

    public Optional<QuestDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<QuestDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparingInt(QuestDefinition::sort).thenComparing(QuestDefinition::id))
                .toList();
    }

    public QuestProgress progress(SkyBlockProfile profile, QuestDefinition definition) {
        double current = Math.max(0.0D, currentValue(profile, definition));
        double required = Math.max(1.0D, definition.required());
        double percent = Math.max(0.0D, Math.min(100.0D, current / required * 100.0D));
        return new QuestProgress(current, required, current >= required, percent);
    }

    public List<TextService.TextPlaceholder> summaryPlaceholders(SkyBlockProfile profile) {
        List<QuestDefinition> quests = definitions();
        long completed = quests.stream()
                .filter(quest -> progress(profile, quest).complete())
                .count();
        return List.of(
                TextService.raw("completed", text.formatNumber(completed)),
                TextService.raw("active", text.formatNumber(Math.max(0, quests.size() - completed))),
                TextService.raw("total", text.formatNumber(quests.size()))
        );
    }

    public List<TextService.TextPlaceholder> placeholders(SkyBlockProfile profile, QuestDefinition definition) {
        QuestProgress progress = progress(profile, definition);
        return List.of(
                TextService.raw("id", definition.id()),
                TextService.parsed("quest", definition.displayName()),
                TextService.parsed("category", definition.category()),
                TextService.parsed("target", targetDisplayName(definition)),
                TextService.raw("current", text.formatNumber(progress.current())),
                TextService.raw("required", text.formatNumber(progress.required())),
                TextService.raw("remaining", text.formatNumber(Math.max(0.0D, progress.required() - progress.current()))),
                TextService.raw("progress", text.formatNumber(progress.percent())),
                TextService.parsed("status", text.rawMessage(progress.complete() ? "quests.status-complete" : "quests.status-active"))
        );
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.quest-log-disabled");
            return;
        }
        List<QuestDefinition> quests = definitions();
        if (quests.isEmpty()) {
            text.send(player, "commands.quest-log-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.quest-log-header", summaryPlaceholders(profile));
        for (QuestDefinition quest : quests) {
            text.send(player, "commands.quest-log-line", placeholders(profile, quest));
        }
    }

    public void sendDetail(Player player, String questId) {
        if (!enabled()) {
            text.send(player, "commands.quest-log-disabled");
            return;
        }
        QuestDefinition quest = definition(questId).orElse(null);
        if (quest == null) {
            text.send(player, "commands.quest-log-unknown", List.of(TextService.raw("quest", questId)));
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.quest-log-detail", placeholders(profile, quest));
        for (String line : quest.description()) {
            text.send(player, "commands.quest-log-description", List.of(TextService.parsed("description", line)));
        }
    }

    private double currentValue(SkyBlockProfile profile, QuestDefinition definition) {
        String target = definition.target() == null ? "" : definition.target().toUpperCase(Locale.ROOT);
        return switch (definition.objectiveType()) {
            case ISLAND_CREATED -> profile.islandWorldName() == null || profile.islandWorldName().isBlank() ? 0.0D : 1.0D;
            case SKYBLOCK_LEVEL -> plugin.skills().skyBlockLevel(profile);
            case TOTAL_SKILL_LEVEL -> totalSkillLevel(profile);
            case SKILL_LEVEL -> SkillType.fromKey(target)
                    .map(type -> (double) plugin.skills().level(type, profile.skillXp(type)))
                    .orElse(0.0D);
            case SKILL_XP -> SkillType.fromKey(target)
                    .map(profile::skillXp)
                    .orElse(0.0D);
            case COLLECTION_AMOUNT -> profile.collectionAmount(target);
            case COLLECTION_TIER -> plugin.collections().tier(profile, target);
            case UNLOCKED_COLLECTIONS -> plugin.collections().definitions().stream()
                    .filter(collection -> profile.collectionAmount(collection.id()) > 0L)
                    .count();
            case SLAYER_LEVEL -> profile.slayerLevels().getOrDefault(target, 0);
            case SLAYER_XP -> profile.slayerXp().getOrDefault(target, 0.0D);
            case BESTIARY_KILLS -> target.isBlank()
                    ? profile.bestiaryKills().values().stream().mapToLong(Long::longValue).sum()
                    : profile.bestiaryKills().getOrDefault(target, 0L);
            case BESTIARY_TIER -> profile.bestiaryTiers().getOrDefault(target, 0);
            case FAIRY_SOULS -> profile.fairySouls().size();
            case PETS_OWNED -> profile.pets().size();
            case PET_SCORE -> plugin.pets().score(profile);
            case MINIONS_PLACED -> profile.minions().size();
            case MUSEUM_DONATIONS -> profile.museumDonations().size();
            case PURSE -> profile.purse();
            case BANK_BALANCE -> profile.bank();
        };
    }

    private int totalSkillLevel(SkyBlockProfile profile) {
        int total = 0;
        for (SkillDefinition definition : plugin.skills().definitions()) {
            total += plugin.skills().level(definition.type(), profile.skillXp(definition.type()));
        }
        return total;
    }

    public String targetDisplayName(QuestDefinition definition) {
        String target = definition.target() == null ? "" : definition.target();
        if (target.isBlank()) {
            return "";
        }
        return switch (definition.objectiveType()) {
            case SKILL_LEVEL, SKILL_XP -> SkillType.fromKey(target)
                    .map(plugin.skills()::definition)
                    .map(SkillDefinition::displayName)
                    .orElse(target);
            case COLLECTION_AMOUNT, COLLECTION_TIER -> plugin.collections().definition(target)
                    .map(CollectionDefinition::displayName)
                    .orElse(target);
            default -> target;
        };
    }
}
