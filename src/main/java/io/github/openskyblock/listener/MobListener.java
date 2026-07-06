package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.mob.SkyBlockMobDefinition;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class MobListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public MobListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCustomMobDeath(EntityDeathEvent event) {
        if (!plugin.mobs().enabled()) {
            return;
        }
        plugin.mobs().handleDeath(event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onCustomMobAttack(EntityDamageByEntityEvent event) {
        if (!plugin.mobs().enabled()) {
            return;
        }
        SkyBlockMobDefinition definition = attackingMob(event);
        if (definition == null || !(event.getEntity() instanceof Player)) {
            return;
        }
        event.setDamage(Math.max(0.0D, definition.damage()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCustomMobDamagedByPlayer(EntityDamageByEntityEvent event) {
        if (!plugin.mobs().enabled()) {
            return;
        }
        SkyBlockMobDefinition definition = plugin.mobs().definition(event.getEntity()).orElse(null);
        if (definition == null || !playerDamager(event)) {
            return;
        }
        double defenseMultiplier = 100.0D / (100.0D + Math.max(0.0D, definition.defense()));
        event.setDamage(Math.max(1.0D, event.getDamage() * defenseMultiplier));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCustomMobDamage(EntityDamageEvent event) {
        if (!plugin.mobs().enabled()) {
            return;
        }
        if (plugin.mobs().definition(event.getEntity()).isPresent()) {
            plugin.mobs().refreshNameNextTick(event.getEntity());
        }
    }

    private SkyBlockMobDefinition attackingMob(EntityDamageByEntityEvent event) {
        SkyBlockMobDefinition direct = plugin.mobs().definition(event.getDamager()).orElse(null);
        if (direct != null) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity entity) {
                return plugin.mobs().definition(entity).orElse(null);
            }
        }
        return null;
    }

    private boolean playerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return true;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Player;
        }
        return false;
    }
}
