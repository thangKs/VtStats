/*
 * VtFlyPlus - An advanced flight management plugin for your servers.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the root of this project for more information.
 */
package com.vtstats;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LangManager {

    private final JavaPlugin plugin;
    private FileConfiguration langConfig;

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadLang();
    }

    public void loadLang() {
        String lang = plugin.getConfig().getString("language", "vi");
        String fileName = "messages_" + lang + ".yml";
        String resourcePath = "language/" + fileName;

        File langFolder = new File(plugin.getDataFolder(), "language");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        File langFile = new File(langFolder, fileName);

        if (!langFile.exists()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("ERROR: Could not find '" + resourcePath + "' in the JAR file.");
                plugin.getLogger().severe("Please make sure you have created the file at 'src/main/resources/" + resourcePath + "'.");
                langConfig = new YamlConfiguration();
                return;
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        try (InputStream defaultStream = plugin.getResource(resourcePath)) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
                langConfig.setDefaults(defaultConfig);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getMessage(String path) {
        String message = langConfig.getString(path);
        if (message == null) {
            return ChatColor.RED + "Language file error: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getPrefixedMessage(String path) {
        return getMessage("prefix") + getMessage(path);
    }
}
