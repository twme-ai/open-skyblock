package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class PetMenuHolder implements InventoryHolder {
    private final Map<Integer, Integer> petIndexes;
    private final Map<Integer, PetMenuAction> actions;
    private Inventory inventory;

    public PetMenuHolder(Map<Integer, Integer> petIndexes, Map<Integer, PetMenuAction> actions) {
        this.petIndexes = petIndexes;
        this.actions = actions;
    }

    public Integer petIndex(int rawSlot) {
        return petIndexes.get(rawSlot);
    }

    public PetMenuAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, PetMenuAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
