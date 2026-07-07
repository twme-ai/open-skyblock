package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ProfileMenuHolder implements InventoryHolder {
    private final Map<Integer, ProfileMenuAction> actions;
    private Inventory inventory;

    public ProfileMenuHolder(Map<Integer, ProfileMenuAction> actions) {
        this.actions = actions;
    }

    public ProfileMenuAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, ProfileMenuAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
