package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

public final class DragonListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public DragonListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onDragonAbilityProjectileDamage(EntityDamageByEntityEvent event) {
        if (plugin.dragons().isAbilityProjectile(event.getDamager()) && event.getDamager() instanceof Projectile projectile) {
            event.setCancelled(true);
            plugin.dragons().handleAbilityProjectileImpact(projectile, event.getEntity().getLocation());
            return;
        }
        Entity source = dragonSource(event.getDamager());
        if (event.getEntity() instanceof Player && source != null) {
            event.setDamage(plugin.dragons().scaleLiveDragonDamage(source, event.getDamage()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDragonDamage(EntityDamageEvent event) {
        if (!plugin.dragons().isLiveDragon(event.getEntity())) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent entityDamage) {
            Player player = playerDamager(entityDamage);
            if (player != null) {
                plugin.dragons().damageLiveDragon(event.getEntity(), player, Math.max(0.0D, event.getFinalDamage()));
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!plugin.dragons().isLiveDragon(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.dragons().handleExternalDeath(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDragonExplode(EntityExplodeEvent event) {
        if (plugin.dragons().isAbilityProjectile(event.getEntity()) && event.getEntity() instanceof Projectile projectile) {
            event.setCancelled(true);
            plugin.dragons().handleAbilityProjectileImpact(projectile, projectile.getLocation());
            return;
        }
        if (dragonSource(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDragonChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.dragons().isLiveDragon(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonPhaseChange(EnderDragonChangePhaseEvent event) {
        EnderDragon.Phase desired = plugin.dragons().desiredPhase(event.getEntity());
        if (desired != null && event.getNewPhase() != EnderDragon.Phase.DYING && event.getNewPhase() != desired) {
            event.setNewPhase(desired);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonAbilityProjectileHit(ProjectileHitEvent event) {
        if (!plugin.dragons().isAbilityProjectile(event.getEntity())) {
            return;
        }
        Location impact = event.getHitEntity() == null
                ? event.getEntity().getLocation()
                : event.getHitEntity().getLocation();
        plugin.dragons().handleAbilityProjectileImpact(event.getEntity(), impact);
    }

    private Player playerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private Entity dragonSource(Entity entity) {
        if (plugin.dragons().isLiveDragon(entity)) {
            return entity;
        }
        if (entity instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter
                && plugin.dragons().isLiveDragon(shooter)) {
            return shooter;
        }
        return null;
    }
}
