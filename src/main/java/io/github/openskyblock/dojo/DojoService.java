package io.github.openskyblock.dojo;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DojoService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final Map<String, DojoChallengeDefinition> challenges = new HashMap<>();
    private final Map<String, DojoBeltDefinition> belts = new HashMap<>();
    private int rankS = 1000;
    private int rankA = 800;
    private int rankB = 600;
    private int rankC = 400;
    private int rankD = 200;

    public DojoService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
    }

    public void reload() {
        rankS = Math.max(0, configService.dojo().getInt("settings.ranks.S", 1000));
        rankA = Math.max(0, configService.dojo().getInt("settings.ranks.A", 800));
        rankB = Math.max(0, configService.dojo().getInt("settings.ranks.B", 600));
        rankC = Math.max(0, configService.dojo().getInt("settings.ranks.C", 400));
        rankD = Math.max(0, configService.dojo().getInt("settings.ranks.D", 200));
        loadChallenges();
        loadBelts();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.dojo", true);
    }

    public List<String> challengeIds() {
        return challengeDefinitions().stream().map(DojoChallengeDefinition::id).toList();
    }

    public List<String> beltIds() {
        return beltDefinitions().stream().map(DojoBeltDefinition::id).toList();
    }

    public Optional<DojoChallengeDefinition> challenge(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(challenges.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<DojoBeltDefinition> belt(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(belts.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<DojoChallengeDefinition> challengeDefinitions() {
        return challenges.values().stream()
                .sorted(Comparator.comparing(DojoChallengeDefinition::id))
                .toList();
    }

    public List<DojoBeltDefinition> beltDefinitions() {
        return belts.values().stream()
                .sorted(Comparator.comparingInt(DojoBeltDefinition::requiredPoints).thenComparing(DojoBeltDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dojo-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        DojoBeltDefinition current = currentBelt(profile).orElse(null);
        text.send(player, "commands.dojo-status", List.of(
                TextService.raw("points", text.formatNumber(totalPoints(profile))),
                TextService.parsed("belt", current == null ? "<gray>None</gray>" : current.displayName()),
                TextService.raw("claimed_belts", Integer.toString(profile.claimedDojoBelts().size())),
                TextService.raw("challenges", Integer.toString(challenges.size())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
        for (DojoChallengeDefinition challenge : challengeDefinitions()) {
            text.send(player, "commands.dojo-status-line", challengePlaceholders(profile, challenge));
        }
    }

    public void sendChallenges(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dojo-disabled");
            return;
        }
        if (challenges.isEmpty()) {
            text.send(player, "commands.dojo-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.dojo-challenge-header");
        for (DojoChallengeDefinition challenge : challengeDefinitions()) {
            text.send(player, "commands.dojo-challenge-line", challengePlaceholders(profile, challenge));
        }
    }

    public void sendBelts(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dojo-disabled");
            return;
        }
        if (belts.isEmpty()) {
            text.send(player, "commands.dojo-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.dojo-belt-header", List.of(TextService.raw("points", text.formatNumber(totalPoints(profile)))));
        for (DojoBeltDefinition belt : beltDefinitions()) {
            text.send(player, "commands.dojo-belt-line", beltPlaceholders(profile, belt));
        }
    }

    public boolean runChallenge(Player player, String challengeId, int rawScore) {
        if (!enabled()) {
            text.send(player, "commands.dojo-disabled");
            return false;
        }
        if (rawScore < 0) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        DojoChallengeDefinition challenge = challenge(challengeId).orElse(null);
        if (challenge == null) {
            text.send(player, "commands.dojo-unknown-challenge", List.of(TextService.raw("challenge", challengeId == null ? "" : challengeId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int score = Math.min(rawScore, challenge.maxScore());
        int previousScore = profile.dojoChallengeScore(challenge.id());
        int previousPoints = challengePoints(challenge, previousScore);
        int newPoints = challengePoints(challenge, score);
        if (score <= previousScore || newPoints <= previousPoints) {
            text.send(player, "commands.dojo-no-improvement", challengePlaceholders(profile, challenge));
            return false;
        }
        int pointsGained = newPoints - previousPoints;
        double combatXp = pointsGained * challenge.combatXpPerPoint();
        double coins = pointsGained * challenge.coinsPerPoint();
        profile.setDojoChallengeScore(challenge.id(), score);
        if (combatXp > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, combatXp);
        }
        if (coins > 0.0D) {
            economy.addPurse(player, coins);
        }
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(challengePlaceholders(profile, challenge));
        placeholders.add(TextService.raw("score", Integer.toString(score)));
        placeholders.add(TextService.raw("points_gained", text.formatNumber(pointsGained)));
        placeholders.add(TextService.raw("combat_xp_gained", text.formatNumber(combatXp)));
        placeholders.add(TextService.raw("coins_gained", text.formatNumber(coins)));
        placeholders.add(TextService.raw("total_points", text.formatNumber(totalPoints(profile))));
        placeholders.add(TextService.parsed("unclaimed_belts", unclaimedUnlockedBelts(profile)));
        text.send(player, "commands.dojo-run-completed", placeholders);
        return true;
    }

    public boolean claimBelts(Player player, String requestedBeltId) {
        if (!enabled()) {
            text.send(player, "commands.dojo-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (requestedBeltId != null && !requestedBeltId.isBlank() && !requestedBeltId.equalsIgnoreCase("all")) {
            DojoBeltDefinition belt = belt(requestedBeltId).orElse(null);
            if (belt == null) {
                text.send(player, "commands.dojo-unknown-belt", List.of(TextService.raw("belt", requestedBeltId)));
                return false;
            }
            return claimBelt(player, profile, belt);
        }
        List<String> claimed = new ArrayList<>();
        for (DojoBeltDefinition belt : beltDefinitions()) {
            if (claimBeltItem(player, profile, belt)) {
                claimed.add(belt.displayName());
            }
        }
        if (claimed.isEmpty()) {
            text.send(player, "commands.dojo-claim-empty");
            return false;
        }
        profiles.save(player);
        text.send(player, "commands.dojo-claimed", List.of(
                TextService.raw("amount", Integer.toString(claimed.size())),
                TextService.parsed("belts", String.join("<gray>, </gray>", claimed))
        ));
        return true;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        int points = totalPoints(profile);
        double xp = 0.0D;
        for (DojoBeltDefinition belt : beltDefinitions()) {
            if (points >= belt.requiredPoints() && profile.hasClaimedDojoBelt(belt.id())) {
                xp += belt.skyBlockXp();
            }
        }
        return Math.max(0.0D, xp);
    }

    private boolean claimBelt(Player player, SkyBlockProfile profile, DojoBeltDefinition belt) {
        if (totalPoints(profile) < belt.requiredPoints()) {
            text.send(player, "commands.dojo-belt-locked", beltPlaceholders(profile, belt));
            return false;
        }
        if (profile.hasClaimedDojoBelt(belt.id())) {
            text.send(player, "commands.dojo-belt-already-claimed", beltPlaceholders(profile, belt));
            return false;
        }
        if (!claimBeltItem(player, profile, belt)) {
            text.send(player, "commands.dojo-belt-missing-item", beltPlaceholders(profile, belt));
            return false;
        }
        profiles.save(player);
        text.send(player, "commands.dojo-belt-claimed", beltPlaceholders(profile, belt));
        return true;
    }

    private boolean claimBeltItem(Player player, SkyBlockProfile profile, DojoBeltDefinition belt) {
        if (totalPoints(profile) < belt.requiredPoints() || profile.hasClaimedDojoBelt(belt.id())) {
            return false;
        }
        CustomItemDefinition item = customItems.definition(belt.itemId()).orElse(null);
        if (item == null) {
            return false;
        }
        giveCustomItem(player, item);
        profile.claimDojoBelt(belt.id());
        return true;
    }

    private Optional<DojoBeltDefinition> currentBelt(SkyBlockProfile profile) {
        int points = totalPoints(profile);
        DojoBeltDefinition current = null;
        for (DojoBeltDefinition belt : beltDefinitions()) {
            if (points >= belt.requiredPoints()) {
                current = belt;
            }
        }
        return Optional.ofNullable(current);
    }

    private int totalPoints(SkyBlockProfile profile) {
        int total = 0;
        for (DojoChallengeDefinition challenge : challengeDefinitions()) {
            total += challengePoints(challenge, profile.dojoChallengeScore(challenge.id()));
        }
        return Math.max(0, total);
    }

    private int challengePoints(DojoChallengeDefinition challenge, int score) {
        int clampedScore = Math.max(0, Math.min(challenge.maxScore(), score));
        return (int) Math.round(clampedScore * Math.max(0.0D, challenge.pointsMultiplier()));
    }

    private void loadChallenges() {
        challenges.clear();
        ConfigurationSection section = configService.dojo().getConfigurationSection("challenges");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection challengeSection = section.getConfigurationSection(id);
            if (challengeSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            challenges.put(normalized, new DojoChallengeDefinition(
                    normalized,
                    challengeSection.getString("display-name", normalized),
                    challengeSection.getString("description", ""),
                    Math.max(1, challengeSection.getInt("max-score", 1000)),
                    Math.max(0.0D, challengeSection.getDouble("points-multiplier", 1.0D)),
                    Math.max(0.0D, challengeSection.getDouble("combat-xp-per-point", 0.0D)),
                    Math.max(0.0D, challengeSection.getDouble("coins-per-point", 0.0D))
            ));
        }
    }

    private void loadBelts() {
        belts.clear();
        ConfigurationSection section = configService.dojo().getConfigurationSection("belts");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection beltSection = section.getConfigurationSection(id);
            if (beltSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            belts.put(normalized, new DojoBeltDefinition(
                    normalized,
                    beltSection.getString("display-name", normalized),
                    Math.max(0, beltSection.getInt("required-points", 0)),
                    beltSection.getString("item", normalized + "_BELT").toUpperCase(Locale.ROOT),
                    Math.max(0.0D, beltSection.getDouble("skyblock-xp", 0.0D))
            ));
        }
    }

    private List<TextService.TextPlaceholder> challengePlaceholders(SkyBlockProfile profile, DojoChallengeDefinition challenge) {
        int score = profile.dojoChallengeScore(challenge.id());
        return List.of(
                TextService.raw("id", challenge.id()),
                TextService.raw("challenge_id", challenge.id()),
                TextService.parsed("challenge", challenge.displayName()),
                TextService.parsed("description", challenge.description()),
                TextService.raw("best_score", Integer.toString(score)),
                TextService.raw("best_points", text.formatNumber(challengePoints(challenge, score))),
                TextService.raw("max_score", Integer.toString(challenge.maxScore())),
                TextService.raw("rank", rank(score)),
                TextService.raw("combat_xp_per_point", text.formatNumber(challenge.combatXpPerPoint())),
                TextService.raw("coins_per_point", text.formatNumber(challenge.coinsPerPoint()))
        );
    }

    private List<TextService.TextPlaceholder> beltPlaceholders(SkyBlockProfile profile, DojoBeltDefinition belt) {
        int points = totalPoints(profile);
        boolean unlocked = points >= belt.requiredPoints();
        boolean claimed = profile.hasClaimedDojoBelt(belt.id());
        return List.of(
                TextService.raw("id", belt.id()),
                TextService.raw("belt_id", belt.id()),
                TextService.parsed("belt", belt.displayName()),
                TextService.raw("required_points", text.formatNumber(belt.requiredPoints())),
                TextService.raw("points", text.formatNumber(points)),
                TextService.raw("item", belt.itemId()),
                TextService.raw("skyblock_xp", text.formatNumber(belt.skyBlockXp())),
                TextService.parsed("status", claimed ? "<green>Claimed</green>" : unlocked ? "<yellow>Unlocked</yellow>" : "<red>Locked</red>")
        );
    }

    private String unclaimedUnlockedBelts(SkyBlockProfile profile) {
        List<String> unlocked = new ArrayList<>();
        int points = totalPoints(profile);
        for (DojoBeltDefinition belt : beltDefinitions()) {
            if (points >= belt.requiredPoints() && !profile.hasClaimedDojoBelt(belt.id())) {
                unlocked.add(belt.displayName());
            }
        }
        return unlocked.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", unlocked);
    }

    private String rank(int score) {
        if (score >= rankS) {
            return "S";
        }
        if (score >= rankA) {
            return "A";
        }
        if (score >= rankB) {
            return "B";
        }
        if (score >= rankC) {
            return "C";
        }
        if (score >= rankD) {
            return "D";
        }
        return "F";
    }

    private void giveCustomItem(Player player, CustomItemDefinition definition) {
        ItemStack itemStack = customItems.createItem(definition);
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
