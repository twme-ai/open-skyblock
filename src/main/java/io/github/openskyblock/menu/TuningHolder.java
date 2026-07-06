package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class TuningHolder implements InventoryHolder {
    private final Map<Integer, String> statSlots;
    private final Map<Integer, TuningAction> actions;
    private Inventory inventory;

    public TuningHolder(Map<Integer, String> statSlots, Map<Integer, TuningAction> actions) {
        this.statSlots = statSlots;
        this.actions = actions;
    }

    public String stat(int rawSlot) {
        return statSlots.get(rawSlot);
    }

    public TuningAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, TuningAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
