package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class SackSelectorHolder implements InventoryHolder {
    private final Map<Integer, String> sacksBySlot;
    private Inventory inventory;

    public SackSelectorHolder(Map<Integer, String> sacksBySlot) {
        this.sacksBySlot = sacksBySlot;
    }

    public String sackId(int rawSlot) {
        return sacksBySlot.get(rawSlot);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
