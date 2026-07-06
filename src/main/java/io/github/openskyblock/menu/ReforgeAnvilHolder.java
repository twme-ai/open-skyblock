package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ReforgeAnvilHolder implements InventoryHolder {
    private final Map<Integer, String> reforgesBySlot;
    private final Map<Integer, ReforgeAnvilAction> actions;
    private Inventory inventory;

    public ReforgeAnvilHolder(Map<Integer, String> reforgesBySlot, Map<Integer, ReforgeAnvilAction> actions) {
        this.reforgesBySlot = reforgesBySlot;
        this.actions = actions;
    }

    public String reforgeId(int rawSlot) {
        return reforgesBySlot.get(rawSlot);
    }

    public ReforgeAnvilAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, ReforgeAnvilAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
