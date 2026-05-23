# FirstJoinRTP

FirstJoinRTP is a lightweight and robust Minecraft server plugin. It facilitates Random Teleport (RTP) for players upon their first time joining a specific world, and automatically saves their destination as their spawnpoint. This is especially useful for servers using Limbo or Lobby authentication plugins (like LibreLogin), where you only want the RTP to trigger when they actually enter the survival world.

## ✨ Features
- **First Join RTP**: Triggers a customizable random teleport command only on their first time joining.
- **Limbo/Lobby Compatible**: Waits until the player enters the configurable `target-world` before triggering.
- **Auto-Save Spawnpoint**: Automatically sets the player's respawn location at their RTP landing spot using the Spigot/Paper API (forced, so beds are not required).
- **Safe Teleportation**: Hides the player during the RTP process, provides temporary invulnerability, and prevents mobs from targeting them while chunks load.
- **Persistent Data**: Uses Minecraft's native `PersistentDataContainer` to track RTP completion, meaning no database configuration is required.

## ⚙️ Configuration
A default `config.yml` is generated upon first startup.

```yaml
# Target world to check for first join RTP
target-world: "world"

# Command to execute for Random Teleport
# Used when the player enters the target world for the first time.
#
# Default: Uses vanilla /spreadplayers command.
# WARNING: Vanilla /spreadplayers runs synchronously on the main thread and can cause
# TPS drops/lag spikes on production servers if the spread radius is large.
#
# Recommendation: For optimal performance, use a dedicated RTP plugin such as BetterRTP:
# rtp-command: "rtp player %player% world %world%"
rtp-command: "execute in %world% run spreadplayers 0 0 150 10000 false %player%"

# Whether to automatically save the player's RTP landing location as their spawnpoint
# using the Minecraft /spawnpoint system (forced, so they don't need a bed to respawn there).
save-spawnpoint: true

# The amount of ticks to wait after the RTP teleport finishes before unhiding the player.
# 20 ticks = 1 second.
delay-after-teleport-ticks: 20
```

## 🚀 Installation
1. Place the `FirstJoinRTP.jar` into your server's `plugins` folder.
2. Restart your server.
3. Adjust `plugins/FirstJoinRTP/config.yml` as needed.
4. Restart your server or reload the plugin to apply config changes.

## 🛠️ Building the Plugin

To compile and package the plugin from source, you will need:
- **Java Development Kit (JDK) 21** or higher.
- **Apache Maven** installed and configured in your system path.
- Run the following command:
   ```cmd
   mvn clean package
   ```
