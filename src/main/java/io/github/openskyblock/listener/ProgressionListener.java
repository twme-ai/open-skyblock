package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.pet.AutoPetTrigger;
import io.github.openskyblock.service.ActionReward;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

public final class ProgressionListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public ProgressionListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        long gatheredAmount = plugin.skills()
                .blockReward(event.getBlock().getType())
                .map(reward -> grantBlockReward(event, reward))
                .orElse(1L);
        plugin.commissions().recordBlockBreak(event.getPlayer(), event.getBlock().getType(), 1L);
        plugin.farmingContests().recordCrop(event.getPlayer(), event.getBlock().getType(), gatheredAmount);
        plugin.miningFiesta().recordBlockBreak(event.getPlayer(), event.getBlock().getType(), event.getBlock().getDrops(event.getPlayer().getInventory().getItemInMainHand(), event.getPlayer()));
        plugin.pets().triggerAutoPet(event.getPlayer(), AutoPetTrigger.BLOCK_BREAK);
    }

    private long grantBlockReward(BlockBreakEvent event, ActionReward reward) {
        Player player = event.getPlayer();
        int extraCopies = fortuneExtraCopies(player, reward.skillType());
        long gatheredAmount = 1L + extraCopies;
        long collectionAmount = reward.collectionAmount() * gatheredAmount;
        plugin.skills().grantActionReward(player, new ActionReward(
                reward.skillType(),
                reward.skillXp(),
                reward.collectionId(),
                collectionAmount,
                reward.coins()
        ));
        if (extraCopies > 0) {
            giveExtraDrops(player, event.getBlock().getDrops(player.getInventory().getItemInMainHand(), player), extraCopies);
        }
        return gatheredAmount;
    }

    private int fortuneExtraCopies(Player player, SkillType skillType) {
        String stat = fortuneStat(skillType);
        if (stat == null) {
            return 0;
        }
        double fortune = Math.max(0.0D, plugin.stats().snapshot(player).stat(stat));
        int extraCopies = (int) Math.floor(fortune / 100.0D);
        double fractionalChance = fortune % 100.0D;
        if (ThreadLocalRandom.current().nextDouble(100.0D) < fractionalChance) {
            extraCopies++;
        }
        return extraCopies;
    }

    private String fortuneStat(SkillType skillType) {
        if (skillType == null) {
            return null;
        }
        return switch (skillType) {
            case FARMING -> "farming_fortune";
            case MINING -> "mining_fortune";
            case FORAGING -> "foraging_fortune";
            default -> null;
        };
    }

    private void giveExtraDrops(Player player, Collection<ItemStack> naturalDrops, int extraCopies) {
        for (ItemStack drop : naturalDrops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            int remaining = drop.getAmount() * extraCopies;
            while (remaining > 0) {
                ItemStack extraDrop = drop.clone();
                int amount = Math.min(extraDrop.getMaxStackSize(), remaining);
                extraDrop.setAmount(amount);
                player.getInventory().addItem(extraDrop).values()
                        .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                remaining -= amount;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        plugin.pets().triggerAutoPet(killer, AutoPetTrigger.KILL);
        plugin.commissions().recordKill(killer, event.getEntityType(), 1L);
        if (plugin.mobs().enabled() && plugin.mobs().definition(event.getEntity()).isPresent()) {
            return;
        }
        plugin.skills()
                .entityReward(event.getEntityType())
                .ifPresent(reward -> plugin.skills().grantActionReward(killer, reward));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack itemStack = event.getItem().getItemStack();
        plugin.skills().grantPickupReward(player, itemStack.getType(), itemStack.getAmount());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCustomItemDamage(EntityDamageByEntityEvent event) {
        if (!plugin.configService().main().getBoolean("features.combat-stats", true)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        StatSnapshot stats = plugin.stats().snapshot(player);
        double damage = Math.max(1.0D, stats.damage());
        double multiplier = 1.0D + Math.max(0.0D, stats.strength()) / 100.0D;
        double customDamage = damage * multiplier;
        if (ThreadLocalRandom.current().nextDouble(100.0D) < Math.max(0.0D, stats.critChance())) {
            customDamage *= 1.0D + Math.max(0.0D, stats.critDamage()) / 100.0D;
        }
        event.setDamage(customDamage);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDefense(EntityDamageEvent event) {
        if (!plugin.configService().main().getBoolean("features.combat-stats", true)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double defense = Math.max(0.0D, plugin.stats().snapshot(player).defense());
        event.setDamage(event.getDamage() * (100.0D / (100.0D + defense)));
    }
}
