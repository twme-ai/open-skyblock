package io.github.openskyblock.farmingcontest;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class FarmingContestService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final Map<String, FarmingContestCropDefinition> crops = new HashMap<>();
    private final Map<String, FarmingContestRewardDefinition> rewards = new HashMap<>();
    private long epochMillis = 0L;
    private long intervalSeconds = 3600L;
    private long durationSeconds = 1200L;
    private int cropsPerContest = 3;
    private long minimumScore = 100L;
    private int leaderboardSize = 10;
    private long lastAwardAttemptIndex = Long.MIN_VALUE;

    public FarmingContestService(ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        this.epochMillis = Math.max(0L, configService.farmingContests().getLong("settings.epoch-millis", 0L));
        this.intervalSeconds = Math.max(60L, configService.farmingContests().getLong("settings.interval-seconds", 3600L));
        this.durationSeconds = Math.max(60L, Math.min(intervalSeconds, configService.farmingContests().getLong("settings.duration-seconds", 1200L)));
        this.cropsPerContest = Math.max(1, configService.farmingContests().getInt("settings.crops-per-contest", 3));
        this.minimumScore = Math.max(1L, configService.farmingContests().getLong("settings.minimum-score", 100L));
        this.leaderboardSize = Math.max(1, Math.min(25, configService.farmingContests().getInt("settings.leaderboard-size", 10)));
        loadCrops();
        loadRewards();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.farming-contests", true);
    }

    public long tickIntervalTicks() {
        return Math.max(20L, configService.farmingContests().getLong("settings.tick-interval-ticks", 100L));
    }

    public List<String> cropIds() {
        return crops().stream().map(FarmingContestCropDefinition::id).toList();
    }

    public List<FarmingContestCropDefinition> crops() {
        return crops.values().stream()
                .sorted(Comparator.comparing(FarmingContestCropDefinition::id))
                .toList();
    }

    public List<FarmingContestRewardDefinition> rewards() {
        return rewards.values().stream()
                .sorted(Comparator.comparingDouble(FarmingContestRewardDefinition::percentile))
                .toList();
    }

    public void tick() {
        if (!enabled()) {
            return;
        }
        long ended = endedContestIndex(System.currentTimeMillis());
        if (ended < 0L || ended == lastAwardAttemptIndex) {
            return;
        }
        awardContest(ended);
        lastAwardAttemptIndex = ended;
    }

    public void recordCrop(Player player, Material material, long amount) {
        if (!enabled() || player == null || material == null || amount <= 0L) {
            return;
        }
        FarmingContestOccurrence occurrence = activeOccurrence().orElse(null);
        if (occurrence == null) {
            return;
        }
        FarmingContestCropDefinition crop = occurrence.crops().stream()
                .filter(candidate -> candidate.material() == material)
                .findFirst()
                .orElse(null);
        if (crop == null) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long before = profile.farmingContestScore(occurrence.id(), crop.id());
        profile.addFarmingContestScore(occurrence.id(), crop.id(), amount);
        long after = profile.farmingContestScore(occurrence.id(), crop.id());
        if (before < minimumScore && after >= minimumScore) {
            text.send(player, "commands.farming-contest-qualified", List.of(
                    TextService.parsed("crop", crop.displayName()),
                    TextService.raw("score", text.formatNumber(after)),
                    TextService.raw("minimum", text.formatNumber(minimumScore))
            ));
        }
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.farming-contest-disabled");
            return;
        }
        tick();
        FarmingContestOccurrence active = activeOccurrence().orElse(null);
        if (active != null) {
            text.send(player, "commands.farming-contest-active", occurrencePlaceholders(active, true));
            sendCurrentScores(player, active);
            return;
        }
        FarmingContestOccurrence next = nextOccurrence();
        text.send(player, "commands.farming-contest-upcoming", occurrencePlaceholders(next, false));
    }

    public void sendCrops(Player player) {
        if (!enabled()) {
            text.send(player, "commands.farming-contest-disabled");
            return;
        }
        if (crops.isEmpty()) {
            text.send(player, "commands.farming-contest-empty");
            return;
        }
        text.send(player, "commands.farming-contest-crops-header");
        for (FarmingContestCropDefinition crop : crops()) {
            text.send(player, "commands.farming-contest-crops-line", cropPlaceholders(crop));
        }
    }

    public void sendRewards(Player player) {
        if (!enabled()) {
            text.send(player, "commands.farming-contest-disabled");
            return;
        }
        if (rewards.isEmpty()) {
            text.send(player, "commands.farming-contest-rewards-empty");
            return;
        }
        text.send(player, "commands.farming-contest-rewards-header", List.of(TextService.raw("minimum", text.formatNumber(minimumScore))));
        for (FarmingContestRewardDefinition reward : rewards()) {
            text.send(player, "commands.farming-contest-rewards-line", rewardPlaceholders(reward));
        }
    }

    public void sendMedals(Player player) {
        if (!enabled()) {
            text.send(player, "commands.farming-contest-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.farming-contest-medals-header", List.of(TextService.raw("tickets", text.formatNumber(profile.jacobsTickets()))));
        for (String medalId : medalIds()) {
            text.send(player, "commands.farming-contest-medals-line", List.of(
                    TextService.raw("medal", medalId),
                    TextService.raw("amount", text.formatNumber(profile.farmingContestMedal(medalId)))
            ));
        }
        activeOccurrence().ifPresent(occurrence -> sendCurrentScores(player, occurrence));
    }

    public void sendLeaderboard(Player player, String rawCropId) {
        if (!enabled()) {
            text.send(player, "commands.farming-contest-disabled");
            return;
        }
        FarmingContestOccurrence occurrence = activeOccurrence().orElseGet(this::previousOrNextOccurrence);
        if (occurrence == null) {
            text.send(player, "commands.farming-contest-empty");
            return;
        }
        FarmingContestCropDefinition crop = cropForLeaderboard(occurrence, rawCropId).orElse(null);
        if (crop == null) {
            text.send(player, "commands.farming-contest-unknown-crop", List.of(TextService.raw("crop", rawCropId == null ? "" : rawCropId)));
            return;
        }
        List<ContestScore> leaderboard = leaderboard(occurrence, crop, 1L).stream()
                .limit(leaderboardSize)
                .toList();
        if (leaderboard.isEmpty()) {
            text.send(player, "commands.farming-contest-leaderboard-empty", occurrenceCropPlaceholders(occurrence, crop));
            return;
        }
        text.send(player, "commands.farming-contest-leaderboard-header", occurrenceCropPlaceholders(occurrence, crop));
        for (int index = 0; index < leaderboard.size(); index++) {
            ContestScore score = leaderboard.get(index);
            text.send(player, "commands.farming-contest-leaderboard-line", List.of(
                    TextService.raw("rank", Integer.toString(index + 1)),
                    TextService.raw("player", score.profile().playerName()),
                    TextService.raw("score", text.formatNumber(score.score()))
            ));
        }
    }

    private void awardContest(long contestIndex) {
        FarmingContestOccurrence occurrence = occurrence(contestIndex);
        Map<UUID, PendingReward> bestRewards = new HashMap<>();
        for (FarmingContestCropDefinition crop : occurrence.crops()) {
            List<ContestScore> leaderboard = leaderboard(occurrence, crop, minimumScore);
            int total = leaderboard.size();
            for (int index = 0; index < leaderboard.size(); index++) {
                Optional<FarmingContestRewardDefinition> reward = rewardForRank(index + 1, total);
                if (reward.isEmpty()) {
                    continue;
                }
                ContestScore score = leaderboard.get(index);
                int order = rewardOrder(reward.get());
                PendingReward pending = new PendingReward(reward.get(), crop, score.score(), order);
                bestRewards.merge(score.profile().uniqueId(), pending, (left, right) -> right.betterThan(left) ? right : left);
            }
        }
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            if (profile.hasFarmingContestReward(occurrence.id())) {
                continue;
            }
            PendingReward reward = bestRewards.get(profile.uniqueId());
            if (reward != null) {
                applyReward(profile, occurrence, reward);
                continue;
            }
            if (totalScore(profile, occurrence) > 0L) {
                profile.setFarmingContestReward(occurrence.id(), "NONE");
            }
        }
        profiles.saveAll();
    }

    private void applyReward(SkyBlockProfile profile, FarmingContestOccurrence occurrence, PendingReward pending) {
        FarmingContestRewardDefinition reward = pending.reward();
        if (!reward.medalId().isBlank() && reward.medalAmount() > 0L) {
            profile.addFarmingContestMedal(reward.medalId(), reward.medalAmount());
        }
        if (reward.tickets() > 0L) {
            profile.addJacobsTickets(reward.tickets());
        }
        profile.setFarmingContestReward(occurrence.id(), reward.id() + ":" + pending.crop().id());
        Player player = Bukkit.getPlayer(profile.uniqueId());
        if (player != null) {
            text.send(player, "commands.farming-contest-rewarded", List.of(
                    TextService.parsed("reward", reward.displayName()),
                    TextService.parsed("crop", pending.crop().displayName()),
                    TextService.raw("score", text.formatNumber(pending.score())),
                    TextService.raw("tickets", text.formatNumber(reward.tickets()))
            ));
        }
    }

    private Optional<FarmingContestRewardDefinition> rewardForRank(int rank, int total) {
        if (total <= 0) {
            return Optional.empty();
        }
        double percentile = (rank - 0.5D) / total;
        return rewards().stream()
                .filter(reward -> percentile <= reward.percentile())
                .findFirst();
    }

    private int rewardOrder(FarmingContestRewardDefinition reward) {
        List<FarmingContestRewardDefinition> sorted = rewards();
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).id().equals(reward.id())) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private List<ContestScore> leaderboard(FarmingContestOccurrence occurrence, FarmingContestCropDefinition crop, long minimum) {
        return profiles.loadedProfiles().stream()
                .map(profile -> new ContestScore(profile, crop, profile.farmingContestScore(occurrence.id(), crop.id())))
                .filter(score -> score.score() >= minimum)
                .sorted(Comparator.comparingLong(ContestScore::score).reversed().thenComparing(score -> score.profile().playerName()))
                .toList();
    }

    private void loadCrops() {
        crops.clear();
        ConfigurationSection section = configService.farmingContests().getConfigurationSection("crops");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection crop = section.getConfigurationSection(id);
            if (crop == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            Material material = Material.matchMaterial(crop.getString("material", normalized));
            crops.put(normalized, new FarmingContestCropDefinition(
                    normalized,
                    crop.getString("display-name", normalized),
                    material == null ? Material.WHEAT : material,
                    crop.getString("collection", normalized).toUpperCase(Locale.ROOT),
                    Math.max(0.0D, crop.getDouble("weight", 1.0D))
            ));
        }
    }

    private void loadRewards() {
        rewards.clear();
        ConfigurationSection section = configService.farmingContests().getConfigurationSection("rewards");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection reward = section.getConfigurationSection(id);
            if (reward == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            rewards.put(normalized, new FarmingContestRewardDefinition(
                    normalized,
                    reward.getString("display-name", normalized),
                    Math.max(0.0D, Math.min(1.0D, reward.getDouble("percentile", 1.0D))),
                    reward.getString("medal", normalized).toUpperCase(Locale.ROOT),
                    Math.max(0L, reward.getLong("medals", 1L)),
                    Math.max(0L, reward.getLong("tickets", 0L))
            ));
        }
    }

    private Optional<FarmingContestOccurrence> activeOccurrence() {
        long now = System.currentTimeMillis();
        if (now < epochMillis) {
            return Optional.empty();
        }
        long index = contestIndex(now);
        FarmingContestOccurrence occurrence = occurrence(index);
        return now >= occurrence.startMillis() && now < occurrence.endMillis() ? Optional.of(occurrence) : Optional.empty();
    }

    private FarmingContestOccurrence nextOccurrence() {
        long now = System.currentTimeMillis();
        if (now < epochMillis) {
            return occurrence(0L);
        }
        long index = contestIndex(now);
        FarmingContestOccurrence occurrence = occurrence(index);
        if (now >= occurrence.endMillis()) {
            return occurrence(index + 1L);
        }
        return occurrence;
    }

    private FarmingContestOccurrence previousOrNextOccurrence() {
        long now = System.currentTimeMillis();
        if (now < epochMillis) {
            return occurrence(0L);
        }
        long index = endedContestIndex(now);
        return occurrence(Math.max(0L, index));
    }

    private FarmingContestOccurrence occurrence(long index) {
        long start = epochMillis + index * intervalSeconds * 1000L;
        return new FarmingContestOccurrence(contestId(index), index, start, start + durationSeconds * 1000L, selectedCrops(index));
    }

    private long contestIndex(long now) {
        if (now < epochMillis) {
            return 0L;
        }
        return Math.floorDiv(now - epochMillis, intervalSeconds * 1000L);
    }

    private long endedContestIndex(long now) {
        if (now < epochMillis) {
            return -1L;
        }
        long index = contestIndex(now);
        FarmingContestOccurrence occurrence = occurrence(index);
        if (now >= occurrence.endMillis()) {
            return index;
        }
        return index - 1L;
    }

    private String contestId(long index) {
        return "JACOB_" + (index + 1L);
    }

    private List<FarmingContestCropDefinition> selectedCrops(long contestIndex) {
        List<FarmingContestCropDefinition> available = crops();
        if (available.size() <= cropsPerContest) {
            return available;
        }
        List<FarmingContestCropDefinition> remaining = new ArrayList<>(available);
        List<FarmingContestCropDefinition> selected = new ArrayList<>();
        Random random = new Random(contestIndex * 65_537L + 17L);
        while (!remaining.isEmpty() && selected.size() < cropsPerContest) {
            double totalWeight = remaining.stream().mapToDouble(FarmingContestCropDefinition::weight).sum();
            int selectedIndex = 0;
            if (totalWeight <= 0.0D) {
                selectedIndex = random.nextInt(remaining.size());
            } else {
                double target = random.nextDouble() * totalWeight;
                double cursor = 0.0D;
                for (int index = 0; index < remaining.size(); index++) {
                    cursor += remaining.get(index).weight();
                    if (target <= cursor) {
                        selectedIndex = index;
                        break;
                    }
                }
            }
            selected.add(remaining.remove(selectedIndex));
        }
        return selected;
    }

    private Optional<FarmingContestCropDefinition> cropForLeaderboard(FarmingContestOccurrence occurrence, String rawCropId) {
        if (rawCropId == null || rawCropId.isBlank()) {
            return occurrence.crops().stream().findFirst();
        }
        return occurrence.crops().stream()
                .filter(crop -> crop.id().equalsIgnoreCase(rawCropId))
                .findFirst();
    }

    private long totalScore(SkyBlockProfile profile, FarmingContestOccurrence occurrence) {
        return occurrence.crops().stream()
                .mapToLong(crop -> profile.farmingContestScore(occurrence.id(), crop.id()))
                .sum();
    }

    private List<String> medalIds() {
        List<String> ids = new ArrayList<>();
        for (FarmingContestRewardDefinition reward : rewards()) {
            if (!reward.medalId().isBlank() && !ids.contains(reward.medalId())) {
                ids.add(reward.medalId());
            }
        }
        return ids;
    }

    private void sendCurrentScores(Player player, FarmingContestOccurrence occurrence) {
        SkyBlockProfile profile = profiles.profile(player);
        for (FarmingContestCropDefinition crop : occurrence.crops()) {
            text.send(player, "commands.farming-contest-score-line", List.of(
                    TextService.parsed("crop", crop.displayName()),
                    TextService.raw("score", text.formatNumber(profile.farmingContestScore(occurrence.id(), crop.id()))),
                    TextService.raw("minimum", text.formatNumber(minimumScore))
            ));
        }
    }

    private List<TextService.TextPlaceholder> occurrencePlaceholders(FarmingContestOccurrence occurrence, boolean active) {
        long now = System.currentTimeMillis();
        return List.of(
                TextService.raw("id", occurrence.id()),
                TextService.parsed("status", text.rawMessage(active ? "farming-contests.status-active" : "farming-contests.status-upcoming")),
                TextService.raw("crops", cropList(occurrence.crops())),
                TextService.raw("remaining", formatDuration(Math.max(0L, ((active ? occurrence.endMillis() : occurrence.startMillis()) - now + 999L) / 1000L))),
                TextService.raw("minimum", text.formatNumber(minimumScore))
        );
    }

    private List<TextService.TextPlaceholder> occurrenceCropPlaceholders(FarmingContestOccurrence occurrence, FarmingContestCropDefinition crop) {
        return List.of(
                TextService.raw("id", occurrence.id()),
                TextService.parsed("crop", crop.displayName()),
                TextService.raw("minimum", text.formatNumber(minimumScore))
        );
    }

    private List<TextService.TextPlaceholder> cropPlaceholders(FarmingContestCropDefinition crop) {
        return List.of(
                TextService.raw("id", crop.id()),
                TextService.parsed("crop", crop.displayName()),
                TextService.raw("material", crop.material().name()),
                TextService.raw("collection", crop.collectionId()),
                TextService.raw("weight", text.formatNumber(crop.weight()))
        );
    }

    private List<TextService.TextPlaceholder> rewardPlaceholders(FarmingContestRewardDefinition reward) {
        return List.of(
                TextService.raw("id", reward.id()),
                TextService.parsed("reward", reward.displayName()),
                TextService.raw("percentile", text.formatNumber(reward.percentile() * 100.0D)),
                TextService.raw("medal", reward.medalId()),
                TextService.raw("medals", text.formatNumber(reward.medalAmount())),
                TextService.raw("tickets", text.formatNumber(reward.tickets()))
        );
    }

    private String cropList(List<FarmingContestCropDefinition> crops) {
        return String.join(", ", crops.stream().map(FarmingContestCropDefinition::id).toList());
    }

    private String formatDuration(long totalSeconds) {
        if (totalSeconds >= 86400L) {
            long days = totalSeconds / 86400L;
            long hours = (totalSeconds % 86400L) / 3600L;
            return days + "d " + hours + "h";
        }
        if (totalSeconds >= 3600L) {
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            return hours + "h " + minutes + "m";
        }
        if (totalSeconds >= 60L) {
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return minutes + "m " + seconds + "s";
        }
        return totalSeconds + "s";
    }

    private record ContestScore(SkyBlockProfile profile, FarmingContestCropDefinition crop, long score) {
    }

    private record PendingReward(FarmingContestRewardDefinition reward, FarmingContestCropDefinition crop, long score, int order) {
        private boolean betterThan(PendingReward other) {
            if (order != other.order()) {
                return order < other.order();
            }
            return score > other.score();
        }
    }
}
