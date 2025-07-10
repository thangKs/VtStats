/*
 * VtFlyPlus - An advanced flight management plugin for your servers.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the root of this project for more information.
 */
package com.vtstats;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogManager {

    private final JavaPlugin plugin;
    private final File logFile;

    public LogManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "logs/vtstats.log");
        setupLogFile();
    }

    private void setupLogFile() {
        if (!logFile.getParentFile().exists()) {
            logFile.getParentFile().mkdirs();
        }
    }

    public void logAction(CommandSender sender, String action, String stat, String target, String value) {
        if (!plugin.getConfig().getBoolean("logging.enable", true)) return;

        String logMessage = String.format("%s performed action '%s' | Stat: %s | Target: %s",
                sender.getName(), action, stat, target);
        if (value != null) {
            logMessage += " | Value: " + value;
        }

        if (plugin.getConfig().getBoolean("logging.log-to-file", true)) {
            logToFile(logMessage);
        } else {
            plugin.getLogger().info(logMessage);
        }
    }

    private void logToFile(String message) {
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            pw.println("[" + timestamp + "] " + message);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write to log file!");
            e.printStackTrace();
        }
    }
}
