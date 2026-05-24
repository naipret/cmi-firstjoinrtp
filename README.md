# FirstJoinRTP

FirstJoinRTP is a lightweight and robust Minecraft server plugin. It facilitates Random Teleport (RTP) for players upon their first time joining a specific world, and automatically executes a configurable command to save their destination as their spawnpoint or home. This is especially useful for servers using Limbo or Lobby authentication plugins (like LibreLogin), where you only want the RTP to trigger when they actually enter the survival world.

## 🎮 Supported Versions

- **Minecraft 1.20.4 - 1.21.x** (Spigot, Paper, Purpur, etc.)
  - *Note:* The native spawnpoint/teleport API methods used by the plugin require Minecraft 1.20.4 or newer.

## ✨ Features

- **First Join RTP**: Triggers a customizable random teleport command only on their first time joining.
- **Auto-Save Spawnpoint / Home**: Execute customizable commands (e.g., vanilla `/spreadplayers` / `/spawnpoint` or CMI `/cmi setspawn` / `/cmi sethome`) at the player's RTP landing spot.
- **Safe Teleportation**: Hides the player during the RTP process, provides temporary invulnerability, and prevents mobs from targeting them while chunks load.
- **Persistent Data**: Uses Minecraft's native `PersistentDataContainer` to track RTP completion, meaning no database configuration is required.

## ⚙️ Configuration

A default `config.yml` is generated upon first startup.

```yaml
# Target world to check for first join RTP
target-world: "world"

# Whether to enable the custom Random Teleport command execution
rtp-command-enabled: true

# Command to execute for Random Teleport
# Used when the player enters the target world for the first time.
#
# Placeholders: %player%, %world%
#
# Default (Vanilla): "spreadplayers 0 0 150 10000 false %player%"
# CMI Recommendation: "cmi rt %player% %world%"
rtp-command: "spreadplayers 0 0 150 10000 false %player%"

# Whether to enable the custom spawnpoint command execution
spawnpoint-command-enabled: true

# Command to set the player's spawnpoint or home after successful RTP.
#
# Placeholders: %player%, %world%, %x%, %y%, %z%
#
# Default (Vanilla): "spawnpoint %player% %x% %y% %z%"
# CMI Recommendation: "cmi sethome firstspawn %player% -p -l:%world%;%x%;%y%;%z% -overwrite"
spawnpoint-command: "spawnpoint %player% %x% %y% %z%"

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

- **Java Development Kit (JDK) 17** or higher.
- **Apache Maven** installed and configured in your system path.
- Run the following command:

   ```cmd
   mvn clean package
   ```
