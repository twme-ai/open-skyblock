package io.github.openskyblock.wardrobe;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class WardrobeService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private int slotCount = 18;

    public WardrobeService(ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        this.slotCount = Math.max(1, Math.min(54, configService.wardrobe().getInt("settings.slots", 18)));
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.wardrobe", true);
    }

    public int slotCount() {
        return slotCount;
    }

    public boolean saveCurrentArmor(Player player, int slot) {
        if (!validate(player, slot)) {
            return false;
        }
        WardrobeSet current = currentArmor(player);
        if (current.empty()) {
            text.send(player, "commands.wardrobe-no-armor");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        WardrobeSet previous = profile.wardrobe().put(slot, current.copy());
        clearArmor(player);
        if (previous != null && !previous.empty()) {
            giveOrDrop(player, previous);
        }
        text.send(player, "commands.wardrobe-saved", placeholders(slot, current));
        return true;
    }

    public boolean swap(Player player, int slot) {
        if (!validate(player, slot)) {
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        WardrobeSet stored = profile.wardrobe().get(slot);
        if (stored == null || stored.empty()) {
            return saveCurrentArmor(player, slot);
        }
        WardrobeSet current = currentArmor(player);
        if (current.empty()) {
            profile.wardrobe().remove(slot);
        } else {
            profile.wardrobe().put(slot, current.copy());
        }
        setArmor(player, stored);
        text.send(player, "commands.wardrobe-equipped", placeholders(slot, stored));
        return true;
    }

    public boolean withdraw(Player player, int slot) {
        if (!validate(player, slot)) {
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        WardrobeSet stored = profile.wardrobe().remove(slot);
        if (stored == null || stored.empty()) {
            text.send(player, "commands.wardrobe-empty", List.of(TextService.raw("slot", Integer.toString(slot))));
            return false;
        }
        giveOrDrop(player, stored);
        text.send(player, "commands.wardrobe-withdrawn", placeholders(slot, stored));
        return true;
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.wardrobe-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.wardrobe-summary-header");
        for (int slot = 1; slot <= slotCount; slot++) {
            WardrobeSet set = profile.wardrobe().get(slot);
            String status = set == null || set.empty()
                    ? text.rawMessage("wardrobe.status-empty")
                    : text.rawMessage("wardrobe.status-stored").replace("<pieces>", Integer.toString(set.pieceCount()));
            text.send(player, "commands.wardrobe-summary-line", List.of(
                    TextService.raw("slot", Integer.toString(slot)),
                    TextService.parsed("status", status)
            ));
        }
    }

    private boolean validate(Player player, int slot) {
        if (!enabled()) {
            text.send(player, "commands.wardrobe-disabled");
            return false;
        }
        if (slot < 1 || slot > slotCount) {
            text.send(player, "commands.wardrobe-invalid-slot", List.of(
                    TextService.raw("slot", Integer.toString(slot)),
                    TextService.raw("max", Integer.toString(slotCount))
            ));
            return false;
        }
        return true;
    }

    private WardrobeSet currentArmor(Player player) {
        PlayerInventory inventory = player.getInventory();
        return WardrobeSet.of(
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        );
    }

    private void setArmor(Player player, WardrobeSet set) {
        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(cloneOrNull(set.helmet()));
        inventory.setChestplate(cloneOrNull(set.chestplate()));
        inventory.setLeggings(cloneOrNull(set.leggings()));
        inventory.setBoots(cloneOrNull(set.boots()));
    }

    private void clearArmor(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(null);
        inventory.setChestplate(null);
        inventory.setLeggings(null);
        inventory.setBoots(null);
    }

    private ItemStack cloneOrNull(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }

    private void giveOrDrop(Player player, WardrobeSet set) {
        for (ItemStack itemStack : set.pieces()) {
            player.getInventory().addItem(itemStack).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private List<TextService.TextPlaceholder> placeholders(int slot, WardrobeSet set) {
        return List.of(
                TextService.raw("slot", Integer.toString(slot)),
                TextService.raw("pieces", Integer.toString(set.pieceCount()))
        );
    }
}
