package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class AccessoryBagHolder implements InventoryHolder {
    private final Map<Integer, AccessoryBagAction> actions;
    private final Map<Integer, String> accessories;
    private Inventory inventory;

    public AccessoryBagHolder(Map<Integer, AccessoryBagAction> actions, Map<Integer, String> accessories) {
        this.actions = actions;
        this.accessories = accessories;
    }

    public AccessoryBagAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, AccessoryBagAction.NONE);
    }

    public String accessory(int rawSlot) {
        return accessories.get(rawSlot);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
