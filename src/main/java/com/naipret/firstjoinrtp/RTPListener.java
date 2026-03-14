package com.naipret.firstjoinrtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Listens for events to manage the first-join RTP process for new players.
 * It waits for the player to enter a specific world, triggers an RTP,
 * and secures their safety during the chunk loading period.
 */
public class RTPListener implements Listener {

    private final FirstJoinRTP plugin;

    // Players who have entered the target world and triggered the RTP, but haven't finished teleporting yet
    private final Set<UUID> inRtpProcess = new HashSet<>();

    // Configuration values
    private final String targetWorld;
    private final String rtpCommand;
    private final String homeName;
    private final String setHomeCommand;
    private final long delayAfterTp;

    /**
     * Constructs a new RTPListener with configuration values loaded.
     *
     * @param plugin The main plugin instance
     */
    public RTPListener(FirstJoinRTP plugin) {
        this.plugin = plugin;
        this.targetWorld = plugin.getConfig().getString("target-world", "world");
        this.rtpCommand = plugin.getConfig().getString("rtp-command", plugin.getConfig().getString("command-to-run", "cmi rt %player% %world%"));
        this.homeName = plugin.getConfig().getString("home-name", "firstjoin");
        this.setHomeCommand = plugin.getConfig().getString("sethome-command", "cmi sethome %homename% %player% -p -l:%world%;%x%;%y%;%z% -overwrite");
        this.delayAfterTp = plugin.getConfig().getLong("delay-after-teleport-ticks", 20L);
    }

    /**
     * Checks when a player joins the server.
     * If they join directly into the target world, start RTP.
     * If they join a Limbo/Lobby world, we wait for PlayerChangedWorldEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (hasCompletedRTP(player)) return;

        // If they bypass Limbo and spawn directly into the target world
        if (player.getWorld().getName().equalsIgnoreCase(targetWorld)) {
            prepareRTP(player);
        }
    }

    /**
     * Listens for players moving between worlds (e.g., leaving a Login Limbo).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        if (hasCompletedRTP(player)) return;

        if (player.getWorld().getName().equalsIgnoreCase(targetWorld)) {
            prepareRTP(player);
        }
    }

    /**
     * Checks if a player has the RTP completed tag in their PersistentDataContainer.
     *
     * @param player The player to check
     * @return true if the player has already successfully undergone RTP natively.
     */
    private boolean hasCompletedRTP(Player player) {
        Byte val = player.getPersistentDataContainer().get(plugin.getRtpCompletedKey(), PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    /**
     * Marks a player as having explicitly completed the RTP process.
     *
     * @param player The player to tag
     */
    private void markCompletedRTP(Player player) {
        player.getPersistentDataContainer().set(plugin.getRtpCompletedKey(), PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Prepares the player for RTP: hides them, makes them invulnerable, 
     * executes the command, and starts a timeout safeguard.
     * 
     * @param player The player to prepare
     */
    private void prepareRTP(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Prevent double execution
        if (inRtpProcess.contains(uuid)) return;

        plugin.getLogger().info("Player " + player.getName() + " entered " + targetWorld + ". Hiding and preparing RTP...");
        
        inRtpProcess.add(uuid);
        
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.hidePlayer(plugin, player);
        }
        
        String cmd = rtpCommand.replace("%player%", player.getName())
                               .replace("%world%", targetWorld);
        
        // Small delay to guarantee they are fully inserted in the world before executing RTP
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
        }.runTaskLater(plugin, 10L); 

        // TIMEOUT SAFEGUARD: 15 seconds (300 ticks)
        // If CMI RTP fails, they would otherwise be stuck invisible and invulnerable forever.
        new BukkitRunnable() {
            @Override
            public void run() {
                // If the player is still in the set after 15s, the teleport event never caught a successful RTP.
                if (inRtpProcess.contains(uuid)) {
                    inRtpProcess.remove(uuid);
                    plugin.getLogger().warning("TIMEOUT: RTP for " + player.getName() + " took too long or failed! Reverting visibility.");
                    
                    if (player.isOnline()) {
                        for (Player other : Bukkit.getOnlinePlayers()) {
                            other.showPlayer(plugin, player);
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 300L);
    }

    /**
     * Listens for teleports to capture the RTP destination and set the player's home there.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (inRtpProcess.contains(uuid)) {
            // Distance check to ignore false positives from micro teleports
            if (event.getFrom().getWorld().equals(event.getTo().getWorld()) && event.getFrom().distanceSquared(event.getTo()) < 100) {
                return;
            }

            // We assume teleport causes like COMMAND, PLUGIN, or UNKNOWN might be the RTP execution.
            // Explicitly ensure the destination world matches the target world to avoid false positives (e.g. anti-cheat snapbacks).
            if (event.getTo().getWorld().getName().equalsIgnoreCase(targetWorld) && 
               (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN)) {

                Location newLoc = event.getTo();

                // Immediately save the destination as a CMI home (circumventing TPS lag)
                String homeCmd = setHomeCommand
                        .replace("%homename%", homeName)
                        .replace("%player%", player.getName())
                        .replace("%world%", newLoc.getWorld().getName())
                        .replace("%x%", String.valueOf(newLoc.getBlockX() + 0.5))
                        .replace("%y%", String.valueOf(newLoc.getBlockY()))
                        .replace("%z%", String.valueOf(newLoc.getBlockZ() + 0.5));

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), homeCmd);

                // Tag the player to prevent future automatic RTPs
                markCompletedRTP(player);
                plugin.getLogger().info("RTP successful for " + player.getName() + ", home '" + homeName + "' set at: " + newLoc.getBlockX() + ", " + newLoc.getBlockY() + ", " + newLoc.getBlockZ());

                // Use the configured delay to ensure the client has loaded chunks before unhiding them
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Prevent potential crashes if the player logs out right after TP
                        if (!player.isOnline()) {
                            inRtpProcess.remove(uuid);
                            return;
                        }

                        // Make the player visible to everyone else again
                        for (Player other : Bukkit.getOnlinePlayers()) {
                            other.showPlayer(plugin, player);
                        }

                        inRtpProcess.remove(uuid);

                        plugin.getLogger().info("Safety period ended for " + player.getName() + ".");
                    }
                }.runTaskLater(plugin, delayAfterTp);
            }
        }
    }

    /**
     * Ensures players in the RTP process are completely invulnerable to damage.
     * E.g fall damage or suffocation because chunks haven't completely loaded.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (inRtpProcess.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents mobs from targeting players that are going through the RTP process.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player) {
            Player player = (Player) event.getTarget();
            if (inRtpProcess.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Cleans up tasks when a player logs off.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        inRtpProcess.remove(uuid);
    }

    /**
     * Cleans up all running tasks and clears collections. Safe to call on plugin disable.
     */
    public void cleanup() {
        inRtpProcess.clear();
    }
}
