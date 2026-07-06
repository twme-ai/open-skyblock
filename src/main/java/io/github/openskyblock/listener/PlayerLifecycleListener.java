package io.github.openskyblock.listener;

import io.github.openskyblock.profile.ProfileManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {
    private final ProfileManager profiles;

    public PlayerLifecycleListener(ProfileManager profiles) {
        this.profiles = profiles;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profiles.profile(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profiles.save(event.getPlayer());
    }
}
