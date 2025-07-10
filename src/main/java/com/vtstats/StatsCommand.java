/*
 * VtFlyPlus - An advanced flight management plugin for your servers.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the root of this project for more information.
 */
package com.vtstats;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final VtStats plugin;
    private final LangManager lang;
    private final LogManager logger;
    private final BackupManager backupManager;

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

    public StatsCommand(VtStats plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLangManager();
        this.logger = plugin.getLogManager();
        this.backupManager = plugin.getBackupManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(lang.getPrefixedMessage("invalid-usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "confirm":
                return handleConfirm(sender);
            case "backup":
                return handleBackup(sender);
            case "loadbackup":
                return handleLoadBackup(sender, args);
            case "reset":
                return handleGlobalReset(sender, args);
            default:
                return handlePlayerCommands(sender, args);
        }
    }
    
    private boolean handleBackup(CommandSender sender) {
        if (!sender.hasPermission("vtstats.backup")) return noPerm(sender);
        backupManager.createBackup(sender, null);
        return true;
    }

    private boolean handleLoadBackup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vtstats.loadbackup")) return noPerm(sender);
        if (args.length != 2) {
            sender.sendMessage(lang.getPrefixedMessage("invalid-usage"));
            return true;
        }
        backupManager.loadBackup(sender, args[1]);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("vtstats.reload")) return noPerm(sender);
        plugin.reload();
        sender.sendMessage(lang.getPrefixedMessage("reload-success"));
        return true;
    }

    private boolean handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }
        plugin.confirmAction((Player) sender);
        return true;
    }

    private boolean handleGlobalReset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
             sender.sendMessage("This dangerous command can only be run by a player.");
             return true;
        }
        if (!sender.hasPermission("vtstats.reset.all")) return noPerm(sender);
        if (args.length != 3 || !args[2].equalsIgnoreCase("all")) {
            sender.sendMessage(lang.getPrefixedMessage("invalid-usage"));
            return true;
        }

        String statArg = args[1];
        Runnable resetTask = () -> {
            if (statArg.equalsIgnoreCase("all")) {
                runResetAllStatsForAllPlayersTask(sender);
            } else {
                runResetSingleStatForAllPlayersTask(sender, statArg);
            }
        };

        Runnable taskWithBackup = () -> {
            if (plugin.getConfig().getBoolean("backup.auto-backup-on-reset", true)) {
                backupManager.createBackup(sender, resetTask);
            } else {
                resetTask.run();
            }
        };

        if (plugin.getConfig().getBoolean("confirmation.required", true)) {
            plugin.requestConfirmation((Player) sender, taskWithBackup);
        } else {
            taskWithBackup.run();
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private boolean handlePlayerCommands(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.getPrefixedMessage("invalid-usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(lang.getPrefixedMessage("player-not-found").replace("{player}", args[0]));
            return true;
        }

        String action = args[1].toLowerCase();
        String statArg = args[2];

        switch (action) {
            case "check":
                return handleCheck(sender, target, statArg);
            case "set":
                if (args.length != 4) return false;
                return handleSet(sender, target, statArg, args[3]);
            case "reset":
                return handleReset(sender, target, statArg);
            default:
                sender.sendMessage(lang.getPrefixedMessage("invalid-usage"));
                return true;
        }
    }

    private boolean handleCheck(CommandSender sender, OfflinePlayer target, String statArg) {
        if (!sender.hasPermission("vtstats.check")) return noPerm(sender);
        try {
            long value = getStatValue(target, statArg);
            sender.sendMessage(lang.getPrefixedMessage("check-stat")
                    .replace("{player}", target.getName()).replace("{stat}", statArg).replace("{value}", String.valueOf(value)));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(lang.getPrefixedMessage("stat-not-found").replace("{stat}", statArg));
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, OfflinePlayer target, String statArg, String valueStr) {
        if (!sender.hasPermission("vtstats.set")) return noPerm(sender);
        if (isProtected(sender, statArg)) return true;

        int value;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.getPrefixedMessage("value-not-a-number").replace("{value}", valueStr));
            return true;
        }

        try {
            setStatValue(target, statArg, value);
            logger.logAction(sender, "set", statArg, target.getName(), valueStr);
            sender.sendMessage(lang.getPrefixedMessage("set-success")
                    .replace("{player}", target.getName()).replace("{stat}", statArg).replace("{value}", valueStr));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(lang.getPrefixedMessage("stat-not-found").replace("{stat}", statArg));
        }
        return true;
    }

    private boolean handleReset(CommandSender sender, OfflinePlayer target, String statArg) {
        if (!sender.hasPermission("vtstats.reset")) return noPerm(sender);
        
        Runnable resetTask = () -> {
            if (statArg.equalsIgnoreCase("all")) {
                runResetAllForPlayerTask(sender, target);
            } else {
                if (isProtected(sender, statArg)) return;
                try {
                    setStatValue(target, statArg, 0);
                    logger.logAction(sender, "reset", statArg, target.getName(), null);
                    sender.sendMessage(lang.getPrefixedMessage("reset-success")
                            .replace("{player}", target.getName()).replace("{stat}", statArg));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(lang.getPrefixedMessage("stat-not-found").replace("{stat}", statArg));
                }
            }
        };

        Runnable taskWithBackup = () -> {
            if (plugin.getConfig().getBoolean("backup.auto-backup-on-reset", true)) {
                backupManager.createBackup(sender, resetTask);
            } else {
                resetTask.run();
            }
        };

        if (statArg.equalsIgnoreCase("all") && plugin.getConfig().getBoolean("confirmation.required", true) && sender instanceof Player) {
            plugin.requestConfirmation((Player) sender, taskWithBackup);
        } else {
            taskWithBackup.run();
        }
        return true;
    }

    private boolean isProtected(CommandSender sender, String statArg) {
        List<String> protectedStats = plugin.getConfig().getStringList("protected-stats");
        String baseStat = statArg.contains(":") ? statArg.split(":", 2)[0] : statArg;
        if (protectedStats.stream().anyMatch(s -> s.equalsIgnoreCase(baseStat))) {
            sender.sendMessage(lang.getPrefixedMessage("stat-is-protected").replace("{stat}", statArg));
            return true;
        }
        return false;
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(lang.getPrefixedMessage("no-permission"));
        return true;
    }

    private void setStatValue(OfflinePlayer p, String stat, int v) throws IllegalArgumentException {
        if (stat.contains(":")) {
            String[] parts = stat.split(":", 2);
            Statistic s = Statistic.valueOf(parts[0].toUpperCase());
            String q = parts[1].toUpperCase();
            if (s.getType() == Statistic.Type.BLOCK || s.getType() == Statistic.Type.ITEM) {
                p.setStatistic(s, Material.matchMaterial(q), v);
            } else if (s.getType() == Statistic.Type.ENTITY) {
                p.setStatistic(s, EntityType.valueOf(q), v);
            }
        } else {
            Statistic s = Statistic.valueOf(stat.toUpperCase());
            if (s.getType() != SIMPLE_STAT_TYPE) throw new IllegalArgumentException("Stat requires a qualifier");
            p.setStatistic(s, v);
        }
    }

    private long getStatValue(OfflinePlayer p, String stat) throws IllegalArgumentException {
        if (stat.contains(":")) {
            String[] parts = stat.split(":", 2);
            Statistic s = Statistic.valueOf(parts[0].toUpperCase());
            String q = parts[1].toUpperCase();
            if (s.getType() == Statistic.Type.BLOCK || s.getType() == Statistic.Type.ITEM) {
                return p.getStatistic(s, Material.matchMaterial(q));
            } else if (s.getType() == Statistic.Type.ENTITY) {
                return p.getStatistic(s, EntityType.valueOf(q));
            }
        } else {
            Statistic s = Statistic.valueOf(stat.toUpperCase());
            if (s.getType() != SIMPLE_STAT_TYPE) throw new IllegalArgumentException("Stat requires a qualifier");
            return p.getStatistic(s);
        }
        return 0;
    }

    private void runResetAllForPlayerTask(CommandSender sender, OfflinePlayer target) {
        logger.logAction(sender, "reset all", "N/A", target.getName(), null);
        sender.sendMessage(lang.getPrefixedMessage("reset-all-success").replace("{player}", target.getName()));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Statistic stat : Statistic.values()) {
                if (!isProtected(sender, stat.name())) {
                    resetStatForAllQualifiers(target, stat);
                }
            }
        });
    }

    private void runResetSingleStatForAllPlayersTask(CommandSender sender, String statArg) {
        logger.logAction(sender, "reset server", statArg, "ALL", null);
        sender.sendMessage(lang.getPrefixedMessage("reset-all-players-start").replace("{stat}", statArg));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) setStatValue(p, statArg, 0);
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(lang.getPrefixedMessage("reset-all-players-finish").replace("{stat}", statArg)));
        });
    }

    private void runResetAllStatsForAllPlayersTask(CommandSender sender) {
        logger.logAction(sender, "reset all server", "N/A", "ALL", null);
        sender.sendMessage(lang.getPrefixedMessage("reset-all-stats-for-all-players-start"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                for (Statistic stat : Statistic.values()) {
                    if (!isProtected(sender, stat.name())) {
                        resetStatForAllQualifiers(p, stat);
                    }
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(lang.getPrefixedMessage("reset-all-stats-for-all-players-finish")));
        });
    }

    private void resetStatForAllQualifiers(OfflinePlayer p, Statistic s) {
        try {
            if (s.getType() == SIMPLE_STAT_TYPE) p.setStatistic(s, 0);
            else if (s.getType() == Statistic.Type.BLOCK) for (Material m : Material.values()) if (m.isBlock()) p.setStatistic(s, m, 0);
            else if (s.getType() == Statistic.Type.ITEM) for (Material mat : Material.values()) if (mat.isItem()) p.setStatistic(s, mat, 0);
            else if (s.getType() == Statistic.Type.ENTITY) for (EntityType et : EntityType.values()) if (et.isAlive()) p.setStatistic(s, et, 0);
        } catch (Exception ignored) {}
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("vtstats.use")) return Collections.emptyList();

        final List<String> suggestions = new ArrayList<>();
        final String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            if (sender.hasPermission("vtstats.reload")) suggestions.add("reload");
            if (sender.hasPermission("vtstats.backup")) suggestions.add("backup");
            if (sender.hasPermission("vtstats.loadbackup")) suggestions.add("loadbackup");
            if (sender.hasPermission("vtstats.reset.all")) suggestions.add("reset");
            suggestions.add("confirm");
            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
            return StringUtil.copyPartialMatches(currentArg, suggestions, new ArrayList<>());
        }

        String mainCommand = args[0].toLowerCase();
        if (mainCommand.equals("reset")) {
            if (args.length == 2 && sender.hasPermission("vtstats.reset.all")) {
                Arrays.stream(Statistic.values()).forEach(s -> suggestions.add(s.name().toLowerCase()));
                suggestions.add("all");
            } else if (args.length == 3 && sender.hasPermission("vtstats.reset.all")) {
                suggestions.add("all");
            }
        } else if (mainCommand.equals("loadbackup")) {
            if (args.length == 2 && sender.hasPermission("vtstats.loadbackup")) {
                suggestions.addAll(backupManager.getBackupList());
            }
        } else { 
            if (args.length == 2) {
                if (sender.hasPermission("vtstats.check")) suggestions.add("check");
                if (sender.hasPermission("vtstats.set")) suggestions.add("set");
                if (sender.hasPermission("vtstats.reset")) suggestions.add("reset");
            } else if (args.length == 3) {
                String action = args[1].toLowerCase();
                if (action.equals("reset")) suggestions.add("all");
                
                String statArg = args[2].toUpperCase();
                if (statArg.contains(":")) {
                    String[] parts = statArg.split(":", 2);
                    try {
                        Statistic stat = Statistic.valueOf(parts[0]);
                        if (stat.getType() == Statistic.Type.BLOCK || stat.getType() == Statistic.Type.ITEM) {
                            Arrays.stream(Material.values())
                                .filter(m -> (stat.getType() == Statistic.Type.BLOCK ? m.isBlock() : m.isItem()))
                                .map(m -> parts[0].toLowerCase() + ":" + m.name().toLowerCase())
                                .forEach(suggestions::add);
                        } else if (stat.getType() == Statistic.Type.ENTITY) {
                            Arrays.stream(EntityType.values()).filter(EntityType::isAlive)
                                .map(e -> parts[0].toLowerCase() + ":" + e.name().toLowerCase())
                                .forEach(suggestions::add);
                        }
                    } catch (IllegalArgumentException ignored) {}
                } else {
                    Arrays.stream(Statistic.values()).map(s -> s.name().toLowerCase()).forEach(suggestions::add);
                }
            }
        }

        return StringUtil.copyPartialMatches(args[args.length - 1], suggestions, new ArrayList<>());
    }
}
