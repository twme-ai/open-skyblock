package io.github.openskyblock.museum;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MuseumService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final Map<String, MuseumDonationDefinition> donations = new HashMap<>();
    private final Map<Integer, MuseumMilestoneDefinition> milestones = new HashMap<>();

    public MuseumService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
    }

    public void reload() {
        donations.clear();
        milestones.clear();
        ConfigurationSection donationSection = configService.museum().getConfigurationSection("donations");
        if (donationSection != null) {
            for (String id : donationSection.getKeys(false)) {
                ConfigurationSection section = donationSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                String normalized = id.toUpperCase(Locale.ROOT);
                String displayName = section.getString("display-name", customItems.definition(normalized)
                        .map(CustomItemDefinition::displayName)
                        .orElse(normalized));
                donations.put(normalized, new MuseumDonationDefinition(
                        normalized,
                        section.getString("category", "MISC").toUpperCase(Locale.ROOT),
                        displayName,
                        Math.max(0.0D, section.getDouble("skyblock-xp", 0.0D)),
                        Math.max(0.0D, section.getDouble("appraisal", 0.0D))
                ));
            }
        }
        ConfigurationSection milestoneSection = configService.museum().getConfigurationSection("milestones");
        if (milestoneSection != null) {
            for (String levelKey : milestoneSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(levelKey);
                    ConfigurationSection section = milestoneSection.getConfigurationSection(levelKey);
                    if (section == null) {
                        continue;
                    }
                    milestones.put(level, new MuseumMilestoneDefinition(
                            level,
                            Math.max(0.0D, section.getDouble("required-skyblock-xp", 0.0D)),
                            Math.max(0.0D, section.getDouble("bank-interest-bonus", 0.0D)),
                            Math.max(0.0D, section.getDouble("bits-multiplier", 0.0D)),
                            section.getStringList("rewards")
                    ));
                } catch (NumberFormatException ignored) {
                    // Invalid milestone keys are skipped.
                }
            }
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.museum", true);
    }

    public Optional<MuseumDonationDefinition> donation(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(donations.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<MuseumDonationDefinition> donations() {
        return donations.values().stream()
                .sorted(Comparator.comparing(MuseumDonationDefinition::category).thenComparing(MuseumDonationDefinition::id))
                .toList();
    }

    public List<MuseumMilestoneDefinition> milestones() {
        return milestones.values().stream()
                .sorted(Comparator.comparingInt(MuseumMilestoneDefinition::level))
                .toList();
    }

    public List<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        categories.add("ALL");
        donations().stream()
                .map(MuseumDonationDefinition::category)
                .forEach(categories::add);
        return List.copyOf(categories);
    }

    public boolean donateHeld(Player player) {
        if (!enabled()) {
            text.send(player, "commands.museum-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = customItems.definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.museum-held-missing");
            return false;
        }
        if (held.getAmount() != 1) {
            text.send(player, "commands.museum-single-item");
            return false;
        }
        MuseumDonationDefinition donation = donation(itemDefinition.id()).orElse(null);
        if (donation == null) {
            text.send(player, "commands.museum-not-accepted", List.of(TextService.parsed("item", itemDefinition.displayName())));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.hasMuseumDonation(donation.id())) {
            text.send(player, "commands.museum-already-donated", donationPlaceholders(profile, donation));
            return false;
        }
        double beforeXp = skyBlockXp(profile);
        if (configService.museum().getBoolean("settings.soulbind-on-donate", true) && !customItems.soulbound(held)) {
            customItems.applySoulbound(held, player, configService.museum().getString("settings.soulbind-type", "COOP"));
        }
        profile.addMuseumDonation(donation.id(), System.currentTimeMillis());
        text.send(player, "commands.museum-donated", donationPlaceholders(profile, donation));
        for (MuseumMilestoneDefinition milestone : newlyUnlocked(beforeXp, skyBlockXp(profile))) {
            text.send(player, "commands.museum-milestone-unlocked", milestonePlaceholders(profile, milestone));
        }
        return true;
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.museum-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.museum-summary", summaryPlaceholders(profile));
    }

    public void sendDonations(Player player, String rawCategory) {
        if (!enabled()) {
            text.send(player, "commands.museum-disabled");
            return;
        }
        String category = rawCategory == null || rawCategory.isBlank() ? "ALL" : rawCategory.toUpperCase(Locale.ROOT);
        List<MuseumDonationDefinition> listed = donations().stream()
                .filter(donation -> category.equals("ALL") || donation.category().equalsIgnoreCase(category))
                .toList();
        if (listed.isEmpty()) {
            if (donations().isEmpty()) {
                text.send(player, "commands.museum-empty");
                return;
            }
            text.send(player, "commands.museum-unknown-category", List.of(TextService.raw("category", rawCategory == null ? "" : rawCategory)));
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.museum-list-header", List.of(
                TextService.raw("category", category),
                TextService.raw("donated", Integer.toString(donatedCount(profile, listed))),
                TextService.raw("total", Integer.toString(listed.size()))
        ));
        for (MuseumDonationDefinition donation : listed) {
            text.send(player, "commands.museum-list-line", donationPlaceholders(profile, donation));
        }
    }

    public void sendMilestones(Player player) {
        if (!enabled()) {
            text.send(player, "commands.museum-disabled");
            return;
        }
        if (milestones.isEmpty()) {
            text.send(player, "commands.museum-milestones-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.museum-milestones-header", summaryPlaceholders(profile));
        for (MuseumMilestoneDefinition milestone : milestones()) {
            text.send(player, "commands.museum-milestone-line", milestonePlaceholders(profile, milestone));
        }
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        return profile.museumDonations().keySet().stream()
                .map(this::donation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToDouble(MuseumDonationDefinition::skyBlockXp)
                .sum();
    }

    public double museumValue(SkyBlockProfile profile) {
        return profile.museumDonations().keySet().stream()
                .map(this::donation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToDouble(MuseumDonationDefinition::appraisalValue)
                .sum();
    }

    public double bankInterestBonus(SkyBlockProfile profile) {
        return milestones().stream()
                .filter(milestone -> skyBlockXp(profile) >= milestone.requiredSkyBlockXp())
                .mapToDouble(MuseumMilestoneDefinition::bankInterestBonus)
                .sum();
    }

    public double bitsMultiplier(SkyBlockProfile profile) {
        return milestones().stream()
                .filter(milestone -> skyBlockXp(profile) >= milestone.requiredSkyBlockXp())
                .mapToDouble(MuseumMilestoneDefinition::bitsMultiplier)
                .sum();
    }

    private int donatedCount(SkyBlockProfile profile, List<MuseumDonationDefinition> listed) {
        int count = 0;
        for (MuseumDonationDefinition donation : listed) {
            if (profile.hasMuseumDonation(donation.id())) {
                count++;
            }
        }
        return count;
    }

    private int donatedCount(SkyBlockProfile profile) {
        return donatedCount(profile, donations());
    }

    private MuseumMilestoneDefinition currentMilestone(SkyBlockProfile profile) {
        double xp = skyBlockXp(profile);
        MuseumMilestoneDefinition current = null;
        for (MuseumMilestoneDefinition milestone : milestones()) {
            if (xp >= milestone.requiredSkyBlockXp()) {
                current = milestone;
            }
        }
        return current;
    }

    private List<MuseumMilestoneDefinition> newlyUnlocked(double beforeXp, double afterXp) {
        List<MuseumMilestoneDefinition> unlocked = new ArrayList<>();
        for (MuseumMilestoneDefinition milestone : milestones()) {
            if (beforeXp < milestone.requiredSkyBlockXp() && afterXp >= milestone.requiredSkyBlockXp()) {
                unlocked.add(milestone);
            }
        }
        return unlocked;
    }

    private List<TextService.TextPlaceholder> summaryPlaceholders(SkyBlockProfile profile) {
        MuseumMilestoneDefinition milestone = currentMilestone(profile);
        return List.of(
                TextService.raw("donated", Integer.toString(donatedCount(profile))),
                TextService.raw("total", Integer.toString(donations.size())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile))),
                TextService.raw("value", text.formatNumber(museumValue(profile))),
                TextService.raw("milestone", milestone == null ? "0" : Integer.toString(milestone.level())),
                TextService.raw("bank_interest_bonus", percent(bankInterestBonus(profile))),
                TextService.raw("bits_multiplier", percent(bitsMultiplier(profile)))
        );
    }

    private List<TextService.TextPlaceholder> donationPlaceholders(SkyBlockProfile profile, MuseumDonationDefinition donation) {
        boolean donated = profile.hasMuseumDonation(donation.id());
        return List.of(
                TextService.raw("id", donation.id()),
                TextService.raw("category", donation.category()),
                TextService.parsed("item", donation.displayName()),
                TextService.raw("skyblock_xp", text.formatNumber(donation.skyBlockXp())),
                TextService.raw("value", text.formatNumber(donation.appraisalValue())),
                TextService.parsed("status", text.rawMessage(donated ? "museum.status-donated" : "museum.status-missing")),
                TextService.raw("total_skyblock_xp", text.formatNumber(skyBlockXp(profile))),
                TextService.raw("total_value", text.formatNumber(museumValue(profile)))
        );
    }

    private List<TextService.TextPlaceholder> milestonePlaceholders(SkyBlockProfile profile, MuseumMilestoneDefinition milestone) {
        boolean unlocked = skyBlockXp(profile) >= milestone.requiredSkyBlockXp();
        return List.of(
                TextService.raw("level", Integer.toString(milestone.level())),
                TextService.raw("required_skyblock_xp", text.formatNumber(milestone.requiredSkyBlockXp())),
                TextService.raw("bank_interest_bonus", percent(milestone.bankInterestBonus())),
                TextService.raw("bits_multiplier", percent(milestone.bitsMultiplier())),
                TextService.parsed("rewards", rewards(milestone)),
                TextService.parsed("status", text.rawMessage(unlocked ? "museum.status-unlocked" : "museum.status-locked"))
        );
    }

    private String rewards(MuseumMilestoneDefinition milestone) {
        if (milestone.rewards().isEmpty()) {
            return text.rawMessage("museum.no-rewards");
        }
        return String.join("<gray>,</gray> ", milestone.rewards());
    }

    private String percent(double value) {
        return text.formatNumber(value * 100.0D) + "%";
    }
}
