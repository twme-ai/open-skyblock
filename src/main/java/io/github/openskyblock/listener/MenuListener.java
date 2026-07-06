package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.menu.AccessoryBagHolder;
import io.github.openskyblock.menu.BankMenuAction;
import io.github.openskyblock.menu.BankMenuHolder;
import io.github.openskyblock.menu.BrowserMenuAction;
import io.github.openskyblock.menu.BrowserMenuHolder;
import io.github.openskyblock.menu.EquipmentHolder;
import io.github.openskyblock.menu.MenuAction;
import io.github.openskyblock.menu.MinionMenuAction;
import io.github.openskyblock.menu.MinionMenuHolder;
import io.github.openskyblock.menu.PetMenuHolder;
import io.github.openskyblock.menu.QuiverHolder;
import io.github.openskyblock.menu.SackMenuAction;
import io.github.openskyblock.menu.SackMenuHolder;
import io.github.openskyblock.menu.SackSelectorHolder;
import io.github.openskyblock.menu.ShopMenuHolder;
import io.github.openskyblock.menu.ShopSelectorHolder;
import io.github.openskyblock.menu.SkyBlockMenuHolder;
import io.github.openskyblock.menu.TuningHolder;
import io.github.openskyblock.menu.WardrobeHolder;
import io.github.openskyblock.service.CustomItemDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class MenuListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public MenuListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SkyBlockMenuHolder holder)) {
            if (event.getView().getTopInventory().getHolder() instanceof MinionMenuHolder minionHolder) {
                handleMinionClick(event, player, minionHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof BrowserMenuHolder browserHolder) {
                handleBrowserClick(event, player, browserHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof BankMenuHolder bankHolder) {
                handleBankClick(event, player, bankHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof ShopSelectorHolder shopSelectorHolder) {
                handleShopSelectorClick(event, player, shopSelectorHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof ShopMenuHolder shopMenuHolder) {
                handleShopClick(event, player, shopMenuHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof SackSelectorHolder sackSelectorHolder) {
                handleSackSelectorClick(event, player, sackSelectorHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof SackMenuHolder sackMenuHolder) {
                handleSackMenuClick(event, player, sackMenuHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof QuiverHolder quiverHolder) {
                handleQuiverClick(event, player, quiverHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof AccessoryBagHolder accessoryBagHolder) {
                handleAccessoryBagClick(event, player, accessoryBagHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof TuningHolder tuningHolder) {
                handleTuningClick(event, player, tuningHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof EquipmentHolder equipmentHolder) {
                handleEquipmentClick(event, player, equipmentHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof WardrobeHolder wardrobeHolder) {
                handleWardrobeClick(event, player, wardrobeHolder);
            }
            if (event.getView().getTopInventory().getHolder() instanceof PetMenuHolder petMenuHolder) {
                handlePetClick(event, player, petMenuHolder);
            }
            return;
        }
        event.setCancelled(true);
        MenuAction action = holder.action(event.getRawSlot());
        if (action == MenuAction.NONE) {
            return;
        }
        player.closeInventory();
        plugin.menus().runAction(player, action);
    }

    private void handleMinionClick(InventoryClickEvent event, Player player, MinionMenuHolder holder) {
        event.setCancelled(true);
        MinionMenuAction action = holder.action(event.getRawSlot());
        if (action == MinionMenuAction.NONE) {
            return;
        }
        plugin.menus().runMinionAction(player, holder, action);
    }

    private void handleBrowserClick(InventoryClickEvent event, Player player, BrowserMenuHolder holder) {
        event.setCancelled(true);
        BrowserMenuAction action = holder.action(event.getRawSlot());
        if (action == BrowserMenuAction.NONE) {
            return;
        }
        plugin.menus().runBrowserAction(player, holder, action);
    }

    private void handleBankClick(InventoryClickEvent event, Player player, BankMenuHolder holder) {
        event.setCancelled(true);
        BankMenuAction action = holder.action(event.getRawSlot());
        if (action == BankMenuAction.NONE) {
            return;
        }
        plugin.menus().runBankAction(player, action);
    }

    private void handleShopSelectorClick(InventoryClickEvent event, Player player, ShopSelectorHolder holder) {
        event.setCancelled(true);
        plugin.menus().runShopSelectorClick(player, holder, event.getRawSlot());
    }

    private void handleShopClick(InventoryClickEvent event, Player player, ShopMenuHolder holder) {
        event.setCancelled(true);
        boolean sellClick = event.getClick().isRightClick();
        plugin.menus().runShopMenuClick(player, holder, event.getRawSlot(), sellClick);
    }

    private void handleSackSelectorClick(InventoryClickEvent event, Player player, SackSelectorHolder holder) {
        event.setCancelled(true);
        plugin.menus().runSackSelectorClick(player, holder, event.getRawSlot());
    }

    private void handleSackMenuClick(InventoryClickEvent event, Player player, SackMenuHolder holder) {
        event.setCancelled(true);
        boolean withdrawAll = event.getClick().isRightClick();
        plugin.menus().runSackMenuClick(player, holder, event.getRawSlot(), withdrawAll);
    }

    private void handleQuiverClick(InventoryClickEvent event, Player player, QuiverHolder holder) {
        event.setCancelled(true);
        boolean withdrawClick = event.getClick().isRightClick();
        plugin.menus().runQuiverClick(player, holder, event.getRawSlot(), withdrawClick);
    }

    private void handleAccessoryBagClick(InventoryClickEvent event, Player player, AccessoryBagHolder holder) {
        event.setCancelled(true);
        plugin.menus().runAccessoryBagClick(player, holder, event.getRawSlot());
    }

    private void handleTuningClick(InventoryClickEvent event, Player player, TuningHolder holder) {
        event.setCancelled(true);
        plugin.menus().runTuningClick(player, holder, event.getRawSlot(), event.getClick().isRightClick());
    }

    private void handleEquipmentClick(InventoryClickEvent event, Player player, EquipmentHolder holder) {
        event.setCancelled(true);
        plugin.menus().runEquipmentClick(player, holder, event.getRawSlot());
    }

    private void handleWardrobeClick(InventoryClickEvent event, Player player, WardrobeHolder holder) {
        event.setCancelled(true);
        plugin.menus().runWardrobeClick(player, holder, event.getRawSlot(), event.getClick().isRightClick());
    }

    private void handlePetClick(InventoryClickEvent event, Player player, PetMenuHolder holder) {
        event.setCancelled(true);
        plugin.menus().runPetMenuClick(player, holder, event.getRawSlot());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onMenuItemUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String itemId = plugin.customItems()
                .definition(event.getItem())
                .map(CustomItemDefinition::id)
                .orElse("");
        if (itemId.isBlank()) {
            return;
        }
        switch (itemId) {
            case "SKYBLOCK_MENU" -> {
                event.setCancelled(true);
                plugin.menus().openSkyBlockMenu(event.getPlayer());
            }
            case "ARROW_SWAPPER" -> {
                event.setCancelled(true);
                plugin.menus().openQuiverMenu(event.getPlayer());
            }
            default -> {
                if (plugin.potions().activateItem(event.getPlayer(), itemId, event.getItem())) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
