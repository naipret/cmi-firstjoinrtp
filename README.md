# FirstJoinRTP and SaveAsSpawn - CMI Addon

FirstJoinRTP and SaveAsSpawn is a lightweight and robust Minecraft server plugin designed specifically as an addon for [CMI](https://www.spigotmc.org/resources/cmi-298-commands-insane-kits-portals-essentials-economy-mysql-sqlite-much-more.3742/). It facilitates Random Teleport (RTP) for players upon their first time joining a specific world, and automatically saves their destination as a home using CMI. This is especially useful for servers using Limbo or Lobby authentication plugins (like LibreLogin), where you only want the RTP to trigger when they actually enter the survival world.

## ✨ Features
- **First Join RTP**: Triggers a CMI random teleport command only on their first time joining.
- **Limbo/Lobby Compatible**: Waits until the player enters the configurable `target-world` before triggering.
- **Auto-Save Home**: Immediately executes `cmi sethome` at the RTP destination to save it as their home base.
- **Safe Teleportation**: Hides the player during the RTP process, provides temporary invulnerability, and prevents mobs from targeting them while chunks load.
- **Persistent Data**: Uses Minecraft's native `PersistentDataContainer` to track RTP completion, meaning no database configuration is required.

## ⚙️ Configuration
A default `config.yml` is generated upon first startup.

```yaml
# Target world to check for first join RTP
target-world: "world"

# Command to execute for Random Teleport
rtp-command: "cmi rt %player% %world%"

# The name of the home to save for the player
home-name: "firstjoin"

# Command to execute to save the home
sethome-command: "cmi sethome %homename% %player% -p -l:%world%;%x%;%y%;%z% -overwrite"

# The amount of ticks to wait after the RTP teleport finishes before unhiding the player.
# 20 ticks = 1 second.
delay-after-teleport-ticks: 20
```

## 🚀 Installation
1. Ensure [CMI](https://www.spigotmc.org/resources/cmi-298-commands-insane-kits-portals-essentials-economy-mysql-sqlite-much-more.3742/) is installed and running on your server.
2. Place the `FirstJoinRTP.jar` into your server's `plugins` folder.
3. Restart your server.
4. Adjust `plugins/FirstJoinRTP/config.yml` as needed.
5. Use `/cmi reload` or restart your server if modifying the CMI aspects.
