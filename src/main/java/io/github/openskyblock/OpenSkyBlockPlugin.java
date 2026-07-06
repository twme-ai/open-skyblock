package io.github.openskyblock;

import io.github.openskyblock.command.SkyBlockCommand;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.listener.PlayerLifecycleListener;
import io.github.openskyblock.listener.ProgressionListener;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.service.CollectionService;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.MinionService;
import io.github.openskyblock.service.SkillService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class OpenSkyBlockPlugin extends JavaPlugin {
    private ConfigService configService;
    private TextService textService;
    private ProfileManager profileManager;
    private SkillService skillService;
    private CollectionService collectionService;
    private CustomItemService customItemService;
    private MinionService minionService;
    private BukkitTask autosaveTask;
    private BukkitTask minionTask;

    @Override
    public void onEnable() {
        this.configService = new ConfigService(this);
        this.configService.load();
        this.textService = new TextService(configService);
        this.profileManager = new ProfileManager(this, configService);
        this.profileManager.loadAll();
        this.collectionService = new CollectionService(configService, textService, profileManager);
        this.skillService = new SkillService(configService, textService, profileManager, collectionService);
        this.customItemService = new CustomItemService(this, configService, textService);
        this.minionService = new MinionService(configService, textService, profileManager, collectionService);

        reloadServices();
        registerCommands();
        registerListeners();
        startTasks();

        getServer().getConsoleSender().sendMessage(textService.message("startup.enabled"));
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (minionTask != null) {
            minionTask.cancel();
        }
        if (profileManager != null) {
            profileManager.saveAll();
        }
        if (textService != null) {
            getServer().getConsoleSender().sendMessage(textService.message("startup.disabled"));
        }
    }

    public void reloadOpenSkyBlock() {
        configService.load();
        reloadServices();
    }

    private void reloadServices() {
        textService.reload();
        collectionService.reload();
        skillService.reload();
        customItemService.reload();
        minionService.reload();
    }

    private void registerCommands() {
        SkyBlockCommand skyBlockCommand = new SkyBlockCommand(this);
        PluginCommand command = getCommand("skyblock");
        if (command == null) {
            throw new IllegalStateException("Command 'skyblock' is missing from paper-plugin.yml");
        }
        command.setExecutor(skyBlockCommand);
        command.setTabCompleter(skyBlockCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(profileManager), this);
        getServer().getPluginManager().registerEvents(new ProgressionListener(this), this);
    }

    private void startTasks() {
        long autosaveTicks = Math.max(1200L, getConfig().getLong("settings.auto-save-ticks", 6000L));
        this.autosaveTask = getServer().getScheduler().runTaskTimer(this, profileManager::saveAll, autosaveTicks, autosaveTicks);
        this.minionTask = getServer().getScheduler().runTaskTimer(this, () -> minionService.tickOnlineMinions(getServer().getOnlinePlayers()), 200L, 200L);
    }

    public ConfigService configService() {
        return configService;
    }

    public TextService text() {
        return textService;
    }

    public ProfileManager profiles() {
        return profileManager;
    }

    public SkillService skills() {
        return skillService;
    }

    public CollectionService collections() {
        return collectionService;
    }

    public CustomItemService customItems() {
        return customItemService;
    }

    public MinionService minions() {
        return minionService;
    }
}
