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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for events to manage the first-join RTP process for new players.
 * It waits for the player to enter a specific world, triggers an RTP,
 * and secures their safety during the chunk loading period.
 */
public class RTPListener implements Listener {

    private final FirstJoinRTP plugin;

    // Maps player UUIDs to their active monitoring tasks
    private final ConcurrentHashMap<UUID, BukkitTask> activeMonitoringTasks = new ConcurrentHashMap<>();

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
     * If they haven't completed RTP before, it starts tracking their location.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // If they already completed the RTP process, ignore them.
        if (hasCompletedRTP(player)) {
            return;
        }

        startMonitoring(player);
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
     * Starts a BukkitTask that periodically checks if the player has entered the target world.
     * Required for setups with Login Limbo phases (AuthMe, LibreLogin, etc).
     *
     * @param player The player being tracked
     */
    private void startMonitoring(Player player) {
        UUID uuid = player.getUniqueId();

        // Prevent registering multiple tracking tasks for the same player.
        if (activeMonitoringTasks.containsKey(uuid)) return;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);

                // Cancel task if the player goes offline unexpectedly
                if (p == null || !p.isOnline()) {
                    cancelTask(uuid);
                    return;
                }

                // If the player successfully enters the target world (e.g., leaves a login Limbo)
                if (p.getWorld().getName().equalsIgnoreCase(targetWorld)) {
                    cancelTask(uuid);

                    plugin.getLogger().info("Player " + p.getName() + " entered " + targetWorld + ". Hiding and preparing RTP...");

                    // Mark player as going through the RTP process
                    inRtpProcess.add(uuid);

                    // Hide the player from others so it doesn't look glitchy while they are being teleported
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        other.hidePlayer(plugin, p);
                    }

                    // Format and dispatch the RTP command
                    String cmd = rtpCommand.replace("%player%", p.getName())
                                           .replace("%world%", targetWorld);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (p.isOnline()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                            }
                        }
                    }.runTaskLater(plugin, 10L); // Small delay to guarantee they are fully inserted in the world before RTP
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second (20 ticks)

        activeMonitoringTasks.put(uuid, task);
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

            // We assume teleport causes like COMMAND, PLUGIN, or UNKNOWN might be the RTP execution
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {

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
        cancelTask(uuid);
        inRtpProcess.remove(uuid);
    }

    /**
     * Internal method to safely cancel and eliminate a monitoring task.
     *
     * @param uuid The unique ID of the player being monitored
     */
    private void cancelTask(UUID uuid) {
        BukkitTask task = activeMonitoringTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Cleans up all running tasks and clears collections. Safe to call on plugin disable.
     */
    public void cleanup() {
        for (BukkitTask task : activeMonitoringTasks.values()) {
            task.cancel();
        }
        activeMonitoringTasks.clear();
        inRtpProcess.clear();
    }
}
