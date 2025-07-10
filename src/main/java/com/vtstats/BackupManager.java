/*
 * VtFlyPlus - An advanced flight management plugin for your servers.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the root of this project for more information.
 */
package com.vtstats;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class BackupManager {

    private final JavaPlugin plugin;
    private final LangManager lang;
    private final File backupRoot;

    private static final Statistic.Type SIMPLE_STAT_TYPE;

    static {
        Statistic.Type tempType;
        try {
            tempType = Statistic.Type.valueOf("UNTIMED");
        } catch (IllegalArgumentException e) {
            tempType = Statistic.Type.valueOf("UNTYPED");
        }
        SIMPLE_STAT_TYPE = tempType;
    }

    public BackupManager(JavaPlugin plugin, LangManager lang) {
        this.plugin = plugin;
        this.lang = lang;
        this.backupRoot = new File(plugin.getDataFolder(), "backups");
        if (!backupRoot.exists()) {
            backupRoot.mkdirs();
        }
    }

    public void createBackup(CommandSender sender, Runnable onFinish) {
        sender.sendMessage(lang.getPrefixedMessage("backup-start"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File backupDir = new File(backupRoot, timestamp);

            if (!backupDir.mkdirs()) {
                sender.sendMessage(lang.getPrefixedMessage("backup-failed"));
                plugin.getLogger().severe("Could not create backup directory: " + backupDir.getPath());
                return;
            }

            manageBackupLimit();

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                File playerFile = new File(backupDir, player.getUniqueId() + ".yml");
                YamlConfiguration playerData = new YamlConfiguration();
                for (Statistic stat : Statistic.values()) {
                    saveStat(playerData, player, stat);
                }
                try {
                    playerData.save(playerFile);
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not save backup for player " + player.getName());
                }
            }
            
            sender.sendMessage(lang.getPrefixedMessage("backup-success").replace("{backup_name}", timestamp));

            if (onFinish != null) {
                Bukkit.getScheduler().runTask(plugin, onFinish);
            }
        });
    }

    public void loadBackup(CommandSender sender, String backupName) {
        File backupDir = new File(backupRoot, backupName);
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            sender.sendMessage(lang.getPrefixedMessage("load-backup-not-found").replace("{backup_name}", backupName));
            return;
        }

        sender.sendMessage(lang.getPrefixedMessage("load-backup-start").replace("{backup_name}", backupName));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File[] playerFiles = backupDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (playerFiles == null) return;

            for (File playerFile : playerFiles) {
                try {
                    UUID uuid = UUID.fromString(playerFile.getName().replace(".yml", ""));
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    if (player.hasPlayedBefore() || player.isOnline()) {
                        YamlConfiguration playerData = YamlConfiguration.loadConfiguration(playerFile);
                        for (String statName : playerData.getKeys(true)) {
                            if (playerData.isConfigurationSection(statName)) continue;
                            loadStatFromString(player, statName, playerData.getInt(statName));
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not load backup for file " + playerFile.getName() + ": " + e.getMessage());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(lang.getPrefixedMessage("load-backup-success").replace("{backup_name}", backupName)));
        });
    }

    public List<String> getBackupList() {
        File[] files = backupRoot.listFiles(File::isDirectory);
        if (files == null) return Collections.emptyList();
        return Arrays.stream(files).map(File::getName).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    private void manageBackupLimit() {
        int maxBackups = plugin.getConfig().getInt("backup.max-backups", 10);
        if (maxBackups <= 0) return;

        File[] backups = backupRoot.listFiles(File::isDirectory);
        if (backups != null && backups.length > maxBackups) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backups.length - maxBackups; i++) {
                deleteDirectory(backups[i]);
            }
        }
    }

    private void deleteDirectory(File directory) {
        File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directory.delete();
    }

    private void saveStat(YamlConfiguration config, OfflinePlayer p, Statistic s) {
        try {
            Statistic.Type type = s.getType();
            if (type == SIMPLE_STAT_TYPE) {
                config.set(s.name(), p.getStatistic(s));
            } else if (type == Statistic.Type.BLOCK || type == Statistic.Type.ITEM) {
                for (Material m : Material.values()) {
                    if ((type == Statistic.Type.BLOCK && m.isBlock()) || (type == Statistic.Type.ITEM && m.isItem())) {
                        int value = p.getStatistic(s, m);
                        if (value > 0) config.set(s.name() + "." + m.name(), value);
                    }
                }
            } else if (type == Statistic.Type.ENTITY) {
                for (EntityType et : EntityType.values()) {
                    if (et.isAlive()) {
                        int value = p.getStatistic(s, et);
                        if (value > 0) config.set(s.name() + "." + et.name(), value);
                    }
                }
            }
        } catch (Exception ignored) {}
    }
    
    private void loadStatFromString(OfflinePlayer p, String fullStatPath, int value) {
        try {
            String[] parts = fullStatPath.split("\\.");
            Statistic s = Statistic.valueOf(parts[0]);
            if (parts.length > 1) {
                String qualifier = parts[1];
                if (s.getType() == Statistic.Type.BLOCK || s.getType() == Statistic.Type.ITEM) {
                    Material m = Material.matchMaterial(qualifier);
                    if (m != null) p.setStatistic(s, m, value);
                } else if (s.getType() == Statistic.Type.ENTITY) {
                    EntityType et = EntityType.valueOf(qualifier);
                    p.setStatistic(s, et, value);
                }
            } else {
                if (s.getType() == SIMPLE_STAT_TYPE) {
                    p.setStatistic(s, value);
                }
            }
        } catch (Exception ignored) {}
    }
}
