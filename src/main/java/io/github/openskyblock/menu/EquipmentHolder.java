package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class EquipmentHolder implements InventoryHolder {
    private final Map<Integer, String> slots;
    private final Map<Integer, EquipmentAction> actions;
    private Inventory inventory;

    public EquipmentHolder(Map<Integer, String> slots, Map<Integer, EquipmentAction> actions) {
        this.slots = slots;
        this.actions = actions;
    }

    public String slotId(int rawSlot) {
        return slots.get(rawSlot);
    }

    public EquipmentAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, EquipmentAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
