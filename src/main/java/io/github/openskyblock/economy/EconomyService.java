package io.github.openskyblock.economy;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.museum.MuseumService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.upgrade.UpgradeService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class EconomyService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private UpgradeService upgrades;
    private MuseumService museum;

    public EconomyService(ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void upgradeService(UpgradeService upgrades) {
        this.upgrades = upgrades;
    }

    public void museumService(MuseumService museum) {
        this.museum = museum;
    }

    public boolean bankEnabled() {
        return configService.main().getBoolean("features.bank", true);
    }

    public double bankCapacity() {
        return Math.max(0.0D, configService.main().getDouble("bank.capacity", 1_000_000.0D));
    }

    public double bankCapacity(SkyBlockProfile profile) {
        double bonus = upgrades == null ? 0.0D : upgrades.capacityBonus(profile, "bank_capacity");
        return Math.max(0.0D, bankCapacity() + bonus);
    }

    public double bankCapacity(Player player) {
        return bankCapacity(profiles.profile(player));
    }

    public void sendBalance(Player player) {
        if (!bankEnabled()) {
            text.send(player, "commands.bank-disabled");
            return;
        }
        applyDueInterest(player);
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.bank-summary", bankPlaceholders(profile));
    }

    public void sendInterestInfo(Player player) {
        if (!bankEnabled()) {
            text.send(player, "commands.bank-disabled");
            return;
        }
        applyDueInterest(player);
        text.send(player, "commands.bank-interest-info", bankPlaceholders(profiles.profile(player)));
    }

    public boolean deposit(Player player, double requestedAmount) {
        return deposit(player, requestedAmount, false);
    }

    private boolean deposit(Player player, double requestedAmount, boolean allowPartial) {
        if (!bankEnabled()) {
            text.send(player, "commands.bank-disabled");
            return false;
        }
        applyDueInterest(player);
        SkyBlockProfile profile = profiles.profile(player);
        double capacity = bankCapacity(profile);
        double space = Math.max(0.0D, capacity - profile.bank());
        if (space <= 0.0D) {
            text.send(player, "commands.bank-full");
            return false;
        }
        if (!allowPartial && requestedAmount > profile.purse()) {
            text.send(player, "commands.bank-no-purse");
            return false;
        }
        if (!allowPartial && requestedAmount > space) {
            text.send(player, "commands.bank-full");
            return false;
        }
        double amount = Math.min(Math.min(requestedAmount, profile.purse()), space);
        if (amount <= 0.0D) {
            text.send(player, "commands.bank-no-purse");
            return false;
        }
        profile.purse(profile.purse() - amount);
        profile.bank(profile.bank() + amount);
        text.send(player, "commands.bank-deposit", List.of(TextService.raw("amount", text.formatNumber(amount))));
        return true;
    }

    public boolean withdraw(Player player, double requestedAmount) {
        if (!bankEnabled()) {
            text.send(player, "commands.bank-disabled");
            return false;
        }
        applyDueInterest(player);
        SkyBlockProfile profile = profiles.profile(player);
        if (requestedAmount > profile.bank()) {
            text.send(player, "commands.bank-no-bank");
            return false;
        }
        double amount = Math.min(requestedAmount, profile.bank());
        if (amount <= 0.0D) {
            text.send(player, "commands.bank-no-bank");
            return false;
        }
        profile.bank(profile.bank() - amount);
        profile.purse(profile.purse() + amount);
        text.send(player, "commands.bank-withdraw", List.of(TextService.raw("amount", text.formatNumber(amount))));
        return true;
    }

    public boolean depositAll(Player player) {
        return deposit(player, profiles.profile(player).purse(), true);
    }

    public boolean withdrawAll(Player player) {
        if (!bankEnabled()) {
            text.send(player, "commands.bank-disabled");
            return false;
        }
        applyDueInterest(player);
        return withdraw(player, profiles.profile(player).bank());
    }

    public boolean spendPurse(Player player, double amount) {
        SkyBlockProfile profile = profiles.profile(player);
        if (amount <= 0.0D) {
            return true;
        }
        if (profile.purse() < amount) {
            return false;
        }
        profile.purse(profile.purse() - amount);
        return true;
    }

    public void addPurse(Player player, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        profile.purse(profile.purse() + amount);
    }

    private void applyDueInterest(Player player) {
        if (!interestEnabled()) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long now = System.currentTimeMillis();
        long intervalMillis = interestIntervalMillis();
        long last = profile.bankInterestLastMillis();
        if (last <= 0L || now < last) {
            profile.bankInterestLastMillis(now);
            return;
        }
        long elapsed = now - last;
        long dueIntervals = elapsed / intervalMillis;
        if (dueIntervals <= 0L) {
            return;
        }
        int payouts = (int) Math.min(dueIntervals, maxInterestAccruals());
        double total = 0.0D;
        for (int index = 0; index < payouts; index++) {
            double space = Math.max(0.0D, bankCapacity(profile) - profile.bank());
            if (space <= 0.0D) {
                break;
            }
            double payout = Math.min(space, interestPayout(profile));
            if (payout <= 0.0D) {
                break;
            }
            profile.bank(profile.bank() + payout);
            total += payout;
        }
        profile.bankInterestLastMillis(last + dueIntervals * intervalMillis);
        if (total > 0.0D) {
            text.send(player, "commands.bank-interest-paid", List.of(
                    TextService.raw("amount", text.formatNumber(total)),
                    TextService.raw("accruals", Integer.toString(payouts)),
                    TextService.raw("next_interest", timeUntilNextInterest(profile))
            ));
        }
    }

    private List<TextService.TextPlaceholder> bankPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.raw("purse", text.formatNumber(profile.purse())),
                TextService.raw("bank", text.formatNumber(profile.bank())),
                TextService.raw("capacity", text.formatNumber(bankCapacity(profile))),
                TextService.raw("interest", text.formatNumber(interestPayout(profile))),
                TextService.raw("next_interest", timeUntilNextInterest(profile)),
                TextService.raw("interest_bonus", percent(museumInterestBonus(profile)))
        );
    }

    private double interestPayout(SkyBlockProfile profile) {
        if (!interestEnabled() || profile.bank() <= 0.0D) {
            return 0.0D;
        }
        List<InterestBracket> brackets = interestBrackets();
        double base = 0.0D;
        double lowerBound = 0.0D;
        for (InterestBracket bracket : brackets) {
            double upperBound = bracket.upTo();
            if (upperBound <= lowerBound) {
                continue;
            }
            double bracketBalance = Math.min(profile.bank(), upperBound) - lowerBound;
            if (bracketBalance > 0.0D) {
                base += bracketBalance * bracket.rate();
            }
            lowerBound = upperBound;
            if (profile.bank() <= upperBound) {
                break;
            }
        }
        double withMuseumBonus = base * (1.0D + museumInterestBonus(profile));
        return Math.min(interestMaxPayout(), Math.max(0.0D, withMuseumBonus));
    }

    private List<InterestBracket> interestBrackets() {
        List<InterestBracket> brackets = new ArrayList<>();
        ConfigurationSection section = configService.main().getConfigurationSection("bank.interest.brackets");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection bracket = section.getConfigurationSection(key);
                if (bracket == null) {
                    continue;
                }
                brackets.add(new InterestBracket(
                        Math.max(0.0D, bracket.getDouble("up-to", 0.0D)),
                        Math.max(0.0D, bracket.getDouble("rate", 0.0D))
                ));
            }
        }
        if (brackets.isEmpty()) {
            brackets.add(new InterestBracket(Double.MAX_VALUE, Math.max(0.0D, configService.main().getDouble("bank.interest.default-rate", 0.02D))));
        }
        return brackets.stream()
                .sorted(Comparator.comparingDouble(InterestBracket::upTo))
                .toList();
    }

    private boolean interestEnabled() {
        return configService.main().getBoolean("bank.interest.enabled", true);
    }

    private long interestIntervalMillis() {
        long seconds = Math.max(60L, configService.main().getLong("bank.interest.interval-seconds", 111600L));
        return seconds * 1000L;
    }

    private int maxInterestAccruals() {
        return Math.max(1, configService.main().getInt("bank.interest.max-accruals", 2));
    }

    private double interestMaxPayout() {
        return Math.max(0.0D, configService.main().getDouble("bank.interest.max-payout", 300000.0D));
    }

    private double museumInterestBonus(SkyBlockProfile profile) {
        return museum == null ? 0.0D : museum.bankInterestBonus(profile);
    }

    private String timeUntilNextInterest(SkyBlockProfile profile) {
        if (!interestEnabled()) {
            return text.rawMessage("bank.interest-disabled");
        }
        long now = System.currentTimeMillis();
        long next = profile.bankInterestLastMillis() + interestIntervalMillis();
        long seconds = Math.max(0L, (next - now + 999L) / 1000L);
        if (seconds <= 0L) {
            return text.rawMessage("bank.interest-ready");
        }
        return formatDuration(seconds);
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }

    private String percent(double value) {
        return text.formatNumber(value * 100.0D) + "%";
    }

    private record InterestBracket(double upTo, double rate) {
    }
}
