package io.github.openskyblock.economy;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.upgrade.UpgradeService;
import java.util.List;
import org.bukkit.entity.Player;

public final class EconomyService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private UpgradeService upgrades;

    public EconomyService(ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void upgradeService(UpgradeService upgrades) {
        this.upgrades = upgrades;
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
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.bank-summary", List.of(
                TextService.raw("purse", text.formatNumber(profile.purse())),
                TextService.raw("bank", text.formatNumber(profile.bank())),
                TextService.raw("capacity", text.formatNumber(bankCapacity(profile)))
        ));
    }

    public boolean deposit(Player player, double requestedAmount) {
        return deposit(player, requestedAmount, false);
    }

    private boolean deposit(Player player, double requestedAmount, boolean allowPartial) {
        if (!bankEnabled()) {
            text.send(player, "commands.bank-disabled");
            return false;
        }
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
}
