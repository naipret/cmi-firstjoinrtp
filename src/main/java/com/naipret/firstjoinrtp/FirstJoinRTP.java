package com.naipret.firstjoinrtp;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class for the FirstJoinRTP plugin.
 * A lightweight CMI addon that safely teleports new players to a random location
 * upon their first join into a specific world, and saves it as their home.
 */
public class FirstJoinRTP extends JavaPlugin {

    private RTPListener rtpListener;
    private NamespacedKey rtpCompletedKey;

    @Override
    public void onEnable() {
        // Save default config.yml if it doesn't exist
        saveDefaultConfig();

        // Key used to track if a player has already been teleported via persistent data
        rtpCompletedKey = new NamespacedKey(this, "rtp_completed");

        // Initialize and register the event listener
        rtpListener = new RTPListener(this);
        getServer().getPluginManager().registerEvents(rtpListener, this);

        getLogger().info("FirstJoinRTP enabled!");
    }

    @Override
    public void onDisable() {
        // Cleanup any pending teleport tasks to prevent memory leaks
        if (rtpListener != null) {
            rtpListener.cleanup();
        }
        getLogger().info("FirstJoinRTP disabled!");
    }

    /**
     * Gets the NamespacedKey used to store the RTP completion status
     * in the player's PersistentDataContainer.
     *
     * @return The NamespacedKey for "rtp_completed"
     */
    public NamespacedKey getRtpCompletedKey() {
        return rtpCompletedKey;
    }
}
