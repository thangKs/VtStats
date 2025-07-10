/*
 * VtFlyPlus - An advanced flight management plugin for your servers.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the root of this project for more information.
 */
package com.vtstats;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VtStats extends JavaPlugin {

    private LangManager langManager;
    private LogManager logManager;
    private BackupManager backupManager;
    private final Map<UUID, ConfirmationTask> pendingConfirmations = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.langManager = new LangManager(this);
        this.logManager = new LogManager(this);
        this.backupManager = new BackupManager(this, langManager);

        PluginCommand command = getCommand("vtstats");
        if (command != null) {
            StatsCommand statsCommand = new StatsCommand(this);
            command.setExecutor(statsCommand);
            command.setTabCompleter(statsCommand);
        }

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            long now = System.currentTimeMillis();
            pendingConfirmations.entrySet().removeIf(entry -> now > entry.getValue().getExpiryTime());
        }, 20L * 10, 20L * 10);

        getLogger().info("VtStats v" + getDescription().getVersion() + " has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("VtStats has been disabled.");
    }

    public void reload() {
        reloadConfig();
        langManager.loadLang();
    }

    public LangManager getLangManager() {
        return langManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public void requestConfirmation(Player player, Runnable task) {
        long timeout = getConfig().getLong("confirmation.timeout-seconds", 30) * 1000;
        ConfirmationTask confirmationTask = new ConfirmationTask(task, System.currentTimeMillis() + timeout);
        pendingConfirmations.put(player.getUniqueId(), confirmationTask);

        String time = String.valueOf(getConfig().getInt("confirmation.timeout-seconds", 30));
        player.sendMessage(langManager.getPrefixedMessage("confirmation-required").replace("{time}", time));
    }

    public boolean confirmAction(Player player) {
        ConfirmationTask task = pendingConfirmations.remove(player.getUniqueId());
        if (task != null && System.currentTimeMillis() <= task.getExpiryTime()) {
            task.getTask().run();
            player.sendMessage(langManager.getPrefixedMessage("confirmation-success"));
            return true;
        } else if (task != null) {
            player.sendMessage(langManager.getPrefixedMessage("confirmation-expired"));
        } else {
            player.sendMessage(langManager.getPrefixedMessage("no-pending-confirmation"));
        }
        return false;
    }

    private static class ConfirmationTask {
        private final Runnable task;
        private final long expiryTime;

        public ConfirmationTask(Runnable task, long expiryTime) {
            this.task = task;
            this.expiryTime = expiryTime;
        }

        public Runnable getTask() {
            return task;
        }

        public long getExpiryTime() {
            return expiryTime;
        }
    }
}
