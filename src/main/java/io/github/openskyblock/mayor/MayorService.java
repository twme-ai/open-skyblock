package io.github.openskyblock.mayor;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
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
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class MayorService {
    private static final String SCORPIUS_ID = "SCORPIUS";

    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final Map<String, MayorCandidateDefinition> candidates = new HashMap<>();
    private long epochMillis = 0L;
    private long cycleSeconds = 446_400L;
    private long electionDurationSeconds = 43_200L;
    private int candidatesPerElection = 5;
    private boolean ministerEnabled = true;
    private int ministerPerkCount = 1;
    private boolean allowVoteChanges = true;
    private List<BribeTier> scorpiusBribeTiers = List.of();

    public MayorService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
    }

    public void reload() {
        this.epochMillis = Math.max(0L, configService.mayors().getLong("settings.epoch-millis", 0L));
        this.cycleSeconds = Math.max(3600L, configService.mayors().getLong("settings.cycle-seconds", 446_400L));
        this.electionDurationSeconds = Math.max(60L, Math.min(cycleSeconds, configService.mayors().getLong("settings.election-duration-seconds", 43_200L)));
        this.candidatesPerElection = Math.max(1, configService.mayors().getInt("settings.candidates-per-election", 5));
        this.ministerEnabled = configService.mayors().getBoolean("settings.minister-enabled", true);
        this.ministerPerkCount = Math.max(1, configService.mayors().getInt("settings.minister-perk-count", 1));
        this.allowVoteChanges = configService.mayors().getBoolean("settings.allow-vote-changes", true);
        this.scorpiusBribeTiers = loadBribeTiers();
        loadCandidates();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.mayors", true);
    }

    public List<String> candidateIds() {
        return candidatePool(currentElectionIndex()).stream()
                .map(MayorCandidateDefinition::id)
                .toList();
    }

    public Optional<MayorCandidateDefinition> candidate(String candidateId) {
        if (candidateId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(candidates.get(candidateId.toUpperCase(Locale.ROOT)));
    }

    public Optional<MayorCandidateDefinition> activeMayor() {
        if (!enabled() || candidates.isEmpty()) {
            return Optional.empty();
        }
        return winner(activeElectionIndex());
    }

    public Optional<MayorCandidateDefinition> activeMinister() {
        if (!enabled() || !ministerEnabled || candidates.size() < 2) {
            return Optional.empty();
        }
        List<MayorCandidateDefinition> ranked = ranked(activeElectionIndex());
        return ranked.size() >= 2 ? Optional.of(ranked.get(1)) : Optional.empty();
    }

    public double modifier(String key) {
        if (!enabled() || key == null || key.isBlank()) {
            return 0.0D;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        double value = activeMayor().stream()
                .flatMap(mayor -> mayor.perks().stream())
                .mapToDouble(perk -> perk.modifiers().getOrDefault(normalized, 0.0D))
                .sum();
        value += activeMinister().stream()
                .flatMap(minister -> minister.perks().stream().limit(ministerPerkCount))
                .mapToDouble(perk -> perk.modifiers().getOrDefault(normalized, 0.0D))
                .sum();
        return value;
    }

    public boolean vote(Player player, String rawCandidateId) {
        if (!enabled()) {
            text.send(player, "commands.mayor-disabled");
            return false;
        }
        if (!electionOpen()) {
            text.send(player, "commands.mayor-vote-closed", List.of(TextService.raw("next", formatDuration(secondsUntilElectionChange()))));
            return false;
        }
        MayorCandidateDefinition candidate = candidate(rawCandidateId).orElse(null);
        if (candidate == null) {
            text.send(player, "commands.mayor-unknown", List.of(TextService.raw("candidate", rawCandidateId == null ? "" : rawCandidateId)));
            return false;
        }
        List<String> candidateIds = candidateIds();
        if (!candidateIds.contains(candidate.id())) {
            text.send(player, "commands.mayor-not-candidate", candidatePlaceholders(candidate, currentElectionId(), voteCounts(currentElectionIndex())));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        String existing = profile.mayorVote(currentElectionId());
        if (existing != null && !existing.isBlank() && !allowVoteChanges) {
            text.send(player, "commands.mayor-already-voted", List.of(TextService.raw("candidate", existing)));
            return false;
        }
        profile.setMayorVote(currentElectionId(), candidate.id());
        profiles.saveAll();
        text.send(player, "commands.mayor-vote-accepted", candidatePlaceholders(candidate, currentElectionId(), voteCounts(currentElectionIndex())));
        return true;
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mayor-disabled");
            return;
        }
        text.send(player, "commands.mayor-status", statusPlaceholders());
    }

    public void sendCandidates(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mayor-disabled");
            return;
        }
        if (candidates.isEmpty()) {
            text.send(player, "commands.mayor-empty");
            return;
        }
        long electionIndex = currentElectionIndex();
        String electionId = electionId(electionIndex);
        Map<String, Integer> votes = voteCounts(electionIndex);
        text.send(player, "commands.mayor-candidates-header", statusPlaceholders());
        for (MayorCandidateDefinition candidate : candidatePool(electionIndex)) {
            text.send(player, "commands.mayor-candidates-line", candidatePlaceholders(candidate, electionId, votes));
        }
    }

    public void sendResults(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mayor-disabled");
            return;
        }
        if (candidates.isEmpty()) {
            text.send(player, "commands.mayor-empty");
            return;
        }
        long electionIndex = electionOpen() ? currentElectionIndex() : activeElectionIndex();
        String electionId = electionId(electionIndex);
        Map<String, Integer> votes = voteCounts(electionIndex);
        text.send(player, "commands.mayor-results-header", List.of(
                TextService.raw("election_id", electionId),
                TextService.raw("votes", Integer.toString(totalVotes(votes)))
        ));
        List<MayorCandidateDefinition> ranked = ranked(electionIndex);
        for (int index = 0; index < ranked.size(); index++) {
            MayorCandidateDefinition candidate = ranked.get(index);
            text.send(player, "commands.mayor-results-line", resultPlaceholders(candidate, votes, index + 1));
        }
    }

    public void sendPerks(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mayor-disabled");
            return;
        }
        Optional<MayorCandidateDefinition> mayor = activeMayor();
        if (mayor.isEmpty()) {
            text.send(player, "commands.mayor-empty");
            return;
        }
        text.send(player, "commands.mayor-perks-header", statusPlaceholders());
        sendPerkLines(player, mayor.get(), text.rawMessage("mayors.role-mayor"), mayor.get().perks());
        activeMinister().ifPresent(minister -> sendPerkLines(player, minister, text.rawMessage("mayors.role-minister"), minister.perks().stream().limit(ministerPerkCount).toList()));
    }

    public boolean claimBribe(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mayor-disabled");
            return false;
        }
        long electionIndex = activeElectionIndex();
        String electionId = electionId(electionIndex);
        MayorCandidateDefinition mayor = winner(electionIndex).orElse(null);
        double playtimeHours = playtimeHours(player);
        double coins = mayor == null ? 0.0D : bribeCoins(mayor, playtimeHours);
        if (mayor == null || !SCORPIUS_ID.equals(mayor.id()) || coins <= 0.0D) {
            text.send(player, "commands.mayor-bribe-unavailable");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (!SCORPIUS_ID.equals(profile.mayorVote(electionId))) {
            text.send(player, "commands.mayor-bribe-vote-required", bribePlaceholders(mayor, electionId, coins, playtimeHours));
            return false;
        }
        if (profile.claimedMayorBribe(electionId)) {
            text.send(player, "commands.mayor-bribe-already-claimed", bribePlaceholders(mayor, electionId, coins, playtimeHours));
            return false;
        }
        profile.setMayorBribeClaimed(electionId, true);
        economy.addPurse(player, coins);
        profiles.save(player);
        text.send(player, "commands.mayor-bribe-claimed", bribePlaceholders(mayor, electionId, coins, playtimeHours));
        return true;
    }

    private void loadCandidates() {
        candidates.clear();
        ConfigurationSection section = configService.mayors().getConfigurationSection("candidates");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection candidateSection = section.getConfigurationSection(id);
            if (candidateSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            candidates.put(normalized, new MayorCandidateDefinition(
                    normalized,
                    candidateSection.getString("display-name", normalized),
                    candidateSection.getString("description", ""),
                    Math.max(0.0D, candidateSection.getDouble("weight", 1.0D)),
                    loadPerks(candidateSection.getConfigurationSection("perks"))
            ));
        }
    }

    private List<MayorPerkDefinition> loadPerks(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<MayorPerkDefinition> perks = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection perkSection = section.getConfigurationSection(id);
            if (perkSection == null) {
                continue;
            }
            Map<String, Double> modifiers = new HashMap<>();
            ConfigurationSection modifierSection = perkSection.getConfigurationSection("modifiers");
            if (modifierSection != null) {
                for (String key : modifierSection.getKeys(false)) {
                    modifiers.put(key.toLowerCase(Locale.ROOT).replace('-', '_'), modifierSection.getDouble(key, 0.0D));
                }
            }
            perks.add(new MayorPerkDefinition(
                    id.toUpperCase(Locale.ROOT),
                    perkSection.getString("display-name", id),
                    perkSection.getString("description", ""),
                    Map.copyOf(modifiers)
            ));
        }
        return List.copyOf(perks);
    }

    private List<BribeTier> loadBribeTiers() {
        ConfigurationSection section = configService.mayors().getConfigurationSection("settings.scorpius-bribe-playtime-tiers");
        if (section == null) {
            return List.of();
        }
        List<BribeTier> tiers = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection tierSection = section.getConfigurationSection(id);
            if (tierSection == null) {
                continue;
            }
            double coins = tierSection.getDouble("coins", 0.0D);
            if (coins <= 0.0D) {
                continue;
            }
            tiers.add(new BribeTier(tierSection.getDouble("max-hours", -1.0D), coins));
        }
        return tiers.stream()
                .sorted(Comparator.comparingDouble(tier -> tier.maxHours() <= 0.0D ? Double.MAX_VALUE : tier.maxHours()))
                .toList();
    }

    private List<MayorCandidateDefinition> candidatePool(long electionIndex) {
        List<MayorCandidateDefinition> available = candidates.values().stream()
                .sorted(Comparator.comparing(MayorCandidateDefinition::id))
                .toList();
        if (available.size() <= candidatesPerElection) {
            return available;
        }
        List<MayorCandidateDefinition> remaining = new ArrayList<>(available);
        List<MayorCandidateDefinition> selected = new ArrayList<>();
        Random random = new Random(electionIndex * 9_973L + 41L);
        while (!remaining.isEmpty() && selected.size() < candidatesPerElection) {
            double totalWeight = remaining.stream().mapToDouble(MayorCandidateDefinition::weight).sum();
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

    private List<MayorCandidateDefinition> ranked(long electionIndex) {
        List<MayorCandidateDefinition> pool = candidatePool(electionIndex);
        Map<String, Integer> votes = voteCounts(electionIndex);
        Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < pool.size(); index++) {
            positions.put(pool.get(index).id(), index);
        }
        return pool.stream()
                .sorted((left, right) -> {
                    int voteCompare = Integer.compare(votes.getOrDefault(right.id(), 0), votes.getOrDefault(left.id(), 0));
                    if (voteCompare != 0) {
                        return voteCompare;
                    }
                    return Integer.compare(positions.getOrDefault(left.id(), 0), positions.getOrDefault(right.id(), 0));
                })
                .toList();
    }

    private Optional<MayorCandidateDefinition> winner(long electionIndex) {
        List<MayorCandidateDefinition> ranked = ranked(electionIndex);
        return ranked.isEmpty() ? Optional.empty() : Optional.of(ranked.getFirst());
    }

    private Map<String, Integer> voteCounts(long electionIndex) {
        String electionId = electionId(electionIndex);
        Map<String, Integer> counts = new HashMap<>();
        for (MayorCandidateDefinition candidate : candidatePool(electionIndex)) {
            counts.put(candidate.id(), 0);
        }
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            String vote = profile.mayorVote(electionId);
            if (vote != null && counts.containsKey(vote)) {
                counts.put(vote, counts.get(vote) + 1);
            }
        }
        return counts;
    }

    private List<TextService.TextPlaceholder> statusPlaceholders() {
        Optional<MayorCandidateDefinition> mayor = activeMayor();
        Optional<MayorCandidateDefinition> minister = activeMinister();
        Map<String, Integer> votes = voteCounts(currentElectionIndex());
        return List.of(
                TextService.raw("election_id", currentElectionId()),
                TextService.parsed("active_mayor", mayor.map(MayorCandidateDefinition::displayName).orElse(text.rawMessage("mayors.no-mayor"))),
                TextService.parsed("minister", minister.map(MayorCandidateDefinition::displayName).orElse(text.rawMessage("mayors.no-minister"))),
                TextService.parsed("phase", text.rawMessage(electionOpen() ? "mayors.phase-election" : "mayors.phase-active")),
                TextService.raw("next_change", formatDuration(secondsUntilElectionChange())),
                TextService.raw("votes", Integer.toString(totalVotes(votes))),
                TextService.raw("candidates", String.join(", ", candidateIds()))
        );
    }

    private List<TextService.TextPlaceholder> candidatePlaceholders(MayorCandidateDefinition candidate, String electionId, Map<String, Integer> votes) {
        return List.of(
                TextService.raw("election_id", electionId),
                TextService.raw("id", candidate.id()),
                TextService.parsed("candidate", candidate.displayName()),
                TextService.raw("description", candidate.description()),
                TextService.raw("votes", Integer.toString(votes.getOrDefault(candidate.id(), 0))),
                TextService.parsed("perks", perkSummary(candidate.perks()))
        );
    }

    private List<TextService.TextPlaceholder> resultPlaceholders(MayorCandidateDefinition candidate, Map<String, Integer> votes, int rank) {
        return List.of(
                TextService.raw("rank", Integer.toString(rank)),
                TextService.raw("id", candidate.id()),
                TextService.parsed("candidate", candidate.displayName()),
                TextService.raw("votes", Integer.toString(votes.getOrDefault(candidate.id(), 0))),
                TextService.parsed("role", resultRole(rank))
        );
    }

    private void sendPerkLines(Player player, MayorCandidateDefinition source, String role, List<MayorPerkDefinition> perks) {
        if (perks.isEmpty()) {
            text.send(player, "commands.mayor-no-perks", List.of(
                    TextService.parsed("source", source.displayName()),
                    TextService.parsed("role", role)
            ));
            return;
        }
        for (MayorPerkDefinition perk : perks) {
            text.send(player, "commands.mayor-perks-line", List.of(
                    TextService.parsed("source", source.displayName()),
                    TextService.parsed("role", role),
                    TextService.parsed("perk", perk.displayName()),
                    TextService.raw("description", perk.description())
            ));
        }
    }

    private double bribeCoins(MayorCandidateDefinition mayor, double playtimeHours) {
        if (!scorpiusBribeTiers.isEmpty()) {
            for (BribeTier tier : scorpiusBribeTiers) {
                if (tier.maxHours() <= 0.0D || playtimeHours < tier.maxHours()) {
                    return tier.coins();
                }
            }
        }
        return mayor.perks().stream()
                .mapToDouble(perk -> perk.modifiers().getOrDefault("election_bribe_coins", 0.0D))
                .sum();
    }

    private double playtimeHours(Player player) {
        return Math.max(0.0D, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000.0D);
    }

    private List<TextService.TextPlaceholder> bribePlaceholders(MayorCandidateDefinition mayor, String electionId, double coins, double playtimeHours) {
        return List.of(
                TextService.raw("election_id", electionId),
                TextService.raw("id", mayor.id()),
                TextService.parsed("candidate", mayor.displayName()),
                TextService.raw("coins", text.formatNumber(coins)),
                TextService.raw("playtime_hours", text.formatNumber(playtimeHours))
        );
    }

    private String perkSummary(List<MayorPerkDefinition> perks) {
        if (perks.isEmpty()) {
            return text.rawMessage("mayors.no-perks");
        }
        return String.join("<gray>,</gray> ", perks.stream().map(MayorPerkDefinition::displayName).toList());
    }

    private String resultRole(int rank) {
        if (rank == 1) {
            return text.rawMessage("mayors.role-mayor");
        }
        if (rank == 2 && ministerEnabled) {
            return text.rawMessage("mayors.role-minister");
        }
        return text.rawMessage("mayors.role-candidate");
    }

    private long currentElectionIndex() {
        long now = System.currentTimeMillis();
        if (now < epochMillis) {
            return 0L;
        }
        return Math.floorDiv(now - epochMillis, cycleSeconds * 1000L);
    }

    private long activeElectionIndex() {
        long current = currentElectionIndex();
        return current <= 0L ? 0L : current - 1L;
    }

    private String currentElectionId() {
        return electionId(currentElectionIndex());
    }

    private String electionId(long electionIndex) {
        return "YEAR_" + (electionIndex + 1L);
    }

    private boolean electionOpen() {
        long now = System.currentTimeMillis();
        long cycleMillis = cycleSeconds * 1000L;
        long electionMillis = electionDurationSeconds * 1000L;
        long index = currentElectionIndex();
        long start = epochMillis + index * cycleMillis;
        long electionStart = start + Math.max(0L, cycleMillis - electionMillis);
        long electionEnd = start + cycleMillis;
        return now >= electionStart && now < electionEnd;
    }

    private long secondsUntilElectionChange() {
        long now = System.currentTimeMillis();
        long cycleMillis = cycleSeconds * 1000L;
        long electionMillis = electionDurationSeconds * 1000L;
        long index = currentElectionIndex();
        long start = epochMillis + index * cycleMillis;
        long electionStart = start + Math.max(0L, cycleMillis - electionMillis);
        long electionEnd = start + cycleMillis;
        long target = electionOpen() ? electionEnd : electionStart;
        if (now >= target) {
            target = start + cycleMillis + Math.max(0L, cycleMillis - electionMillis);
        }
        return Math.max(0L, (target - now + 999L) / 1000L);
    }

    private int totalVotes(Map<String, Integer> votes) {
        return votes.values().stream().mapToInt(Integer::intValue).sum();
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

    private record BribeTier(double maxHours, double coins) {
    }
}
