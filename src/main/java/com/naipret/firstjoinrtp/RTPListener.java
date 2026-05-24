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
 * Listens for events to manage the first-join RTP process for new players. It waits for the player
 * to enter a specific world, triggers an RTP, and secures their safety during the chunk loading
 * period.
 */
public class RTPListener implements Listener {

    private final FirstJoinRTP plugin;

    // Players currently in the 10-tick delay before the RTP command is dispatched
    private final Set<UUID> pendingRtp = new HashSet<>();

    // Players who have entered the target world and triggered the RTP, but haven't finished
    // teleporting yet
    private final Set<UUID> inRtpProcess = new HashSet<>();

    // Configuration values
    private final String targetWorld;
    private final boolean rtpCommandEnabled;
    private final String rtpCommand;
    private final boolean spawnpointCommandEnabled;
    private final String spawnpointCommand;
    private final boolean saveSpawnpoint;
    private final long delayAfterTp;

    /**
     * Constructs a new RTPListener with configuration values loaded.
     *
     * @param plugin The main plugin instance
     */
    public RTPListener(FirstJoinRTP plugin) {
        this.plugin = plugin;
        this.targetWorld = plugin.getConfig().getString("target-world", "world");
        this.rtpCommandEnabled = plugin.getConfig().getBoolean("rtp-command-enabled", true);
        this.rtpCommand = plugin.getConfig().getString("rtp-command",
                "spreadplayers 0 0 150 10000 false %player%");
        this.spawnpointCommandEnabled =
                plugin.getConfig().getBoolean("spawnpoint-command-enabled", true);
        this.spawnpointCommand = plugin.getConfig().getString("spawnpoint-command",
                "spawnpoint %player% %x% %y% %z%");
        this.saveSpawnpoint = plugin.getConfig().getBoolean("save-spawnpoint", true);
        this.delayAfterTp = plugin.getConfig().getLong("delay-after-teleport-ticks", 20L);
    }

    /**
     * Checks when a player joins the server. If they join directly into the target world, start
     * RTP. If they join a Limbo/Lobby world, we wait for PlayerChangedWorldEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (hasCompletedRTP(player))
            return;

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

        if (hasCompletedRTP(player))
            return;

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
        Byte val = player.getPersistentDataContainer().get(plugin.getRtpCompletedKey(),
                PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    /**
     * Marks a player as having explicitly completed the RTP process.
     *
     * @param player The player to tag
     */
    private void markCompletedRTP(Player player) {
        player.getPersistentDataContainer().set(plugin.getRtpCompletedKey(),
                PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Prepares the player for RTP: hides them, makes them invulnerable, executes the command, and
     * starts a timeout safeguard.
     *
     * @param player The player to prepare
     */
    private void prepareRTP(Player player) {
        if (!rtpCommandEnabled) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Prevent double execution during delay or active RTP
        if (pendingRtp.contains(uuid) || inRtpProcess.contains(uuid))
            return;

        plugin.getLogger().info("Player " + player.getName() + " entered " + targetWorld
                + ". Hiding and preparing RTP...");

        pendingRtp.add(uuid);

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.hidePlayer(plugin, player);
        }

        // Small delay to guarantee they are fully inserted in the world before executing RTP
        new BukkitRunnable() {
            @Override
            public void run() {
                pendingRtp.remove(uuid);

                if (player.isOnline()) {
                    // Only start capturing teleports now to avoid catching other plugins' initial
                    // positioning teleports
                    inRtpProcess.add(uuid);
                    dispatchCustomCommand(player, rtpCommand, null);

                    // TIMEOUT SAFEGUARD: 15 seconds (300 ticks) from execution
                    // If RTP command fails, they would otherwise be stuck invisible and
                    // invulnerable forever.
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (inRtpProcess.contains(uuid)) {
                                inRtpProcess.remove(uuid);
                                plugin.getLogger().warning("TIMEOUT: RTP for " + player.getName()
                                        + " took too long or failed! Reverting visibility.");

                                if (player.isOnline()) {
                                    for (Player other : Bukkit.getOnlinePlayers()) {
                                        other.showPlayer(plugin, player);
                                    }
                                }
                            }
                        }
                    }.runTaskLater(plugin, 300L);
                }
            }
        }.runTaskLater(plugin, 10L);
    }

    /**
     * Listens for teleports to capture the RTP destination and set the player's spawnpoint there.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (inRtpProcess.contains(uuid)) {
            // Distance check to ignore false positives from micro teleports
            if (event.getFrom().getWorld().equals(event.getTo().getWorld())
                    && event.getFrom().distanceSquared(event.getTo()) < 100) {
                return;
            }

            // We assume teleport causes like COMMAND, PLUGIN, or UNKNOWN might be the RTP
            // execution.
            // Explicitly ensure the destination world matches the target world to avoid false
            // positives.
            if (event.getTo().getWorld().getName().equalsIgnoreCase(targetWorld)
                    && (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND
                            || event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                            || event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN)) {

                Location newLoc = event.getTo();

                // Determine whether to execute spawnpoint command, set native spawnpoint, or do
                // nothing
                boolean hasNewConfig = plugin.getConfig().contains("spawnpoint-command-enabled")
                        || plugin.getConfig().contains("spawnpoint-command");

                if (hasNewConfig) {
                    if (spawnpointCommandEnabled && spawnpointCommand != null
                            && !spawnpointCommand.trim().isEmpty()) {
                        dispatchCustomCommand(player, spawnpointCommand, newLoc);
                        plugin.getLogger().info("RTP successful for " + player.getName()
                                + ", executed spawnpoint command: " + spawnpointCommand);
                    } else {
                        plugin.getLogger().info("RTP successful for " + player.getName()
                                + " (spawnpoint command is disabled or empty).");
                    }
                } else {
                    // Backwards compatibility for older configs
                    if (saveSpawnpoint) {
                        player.setRespawnLocation(newLoc, true);
                        plugin.getLogger().info("RTP successful for " + player.getName()
                                + ", spawnpoint set natively.");
                    } else {
                        plugin.getLogger().info("RTP successful for " + player.getName()
                                + " (native spawnpoint is disabled).");
                    }
                }

                // Tag the player to prevent future automatic RTPs
                markCompletedRTP(player);

                // Use the configured delay to ensure the client has loaded chunks before unhiding
                // them
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

                        plugin.getLogger()
                                .info("Safety period ended for " + player.getName() + ".");
                    }
                }.runTaskLater(plugin, delayAfterTp);
            }
        }
    }

    /**
     * Ensures players in the RTP process are completely invulnerable to damage. E.g. fall damage or
     * suffocation because chunks haven't completely loaded.
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
        pendingRtp.remove(uuid);
    }

    /**
     * Replaces placeholders and executes a command from the console.
     *
     * @param player The target player
     * @param rawCommand The raw command string from configuration
     * @param loc The landing location (can be null for RTP command)
     */
    private void dispatchCustomCommand(Player player, String rawCommand, Location loc) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return;
        }

        String command = rawCommand.trim();

        // Strip leading slash if present
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }

        // Replace placeholders
        String worldName =
                (loc != null && loc.getWorld() != null) ? loc.getWorld().getName() : targetWorld;
        command = command.replace("%player%", player.getName()).replace("%world%", worldName);

        if (loc != null) {
            command = command.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            command = command.replace("%yaw%", String.valueOf(Math.round(loc.getYaw())))
                    .replace("%pitch%", String.valueOf(Math.round(loc.getPitch())));
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * Cleans up all running tasks and clears collections. Safe to call on plugin disable.
     */
    public void cleanup() {
        inRtpProcess.clear();
        pendingRtp.clear();
    }
}
