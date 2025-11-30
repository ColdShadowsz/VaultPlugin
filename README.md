# VaultPlugin

**VaultPlugin** is a configurable Minecraft plugin that adds personal vaults for players with a selector GUI and economy-based unlocking system. (1.20.6)

---

## Features

- Player-specific vaults with up to 28 vault slots per page.
- Vault selector GUI with navigation and locked/unlocked indicators.
- Supports Vault economy integration for unlocking vaults.
- Fully configurable vault costs via `config.yml`.
- Automatic storage of vault contents per player.
- Safe filler items to prevent players from interacting with UI components.
- Supports up to 28 vaults per player and 54-slot selector GUI.

---

## Requirements

- Java 17–21
- PaperMC / Spigot 1.13+ (built and tested on 1.20.6)
- [Vault](https://www.spigotmc.org/resources/vault.34315/) plugin for economy support.

> ⚠️ Note: VaultPlugin is officially built for **1.20.6**. Users can manually change the `api-version` in `plugin.yml` or recompile the plugin for other Paper/Spigot versions if needed.

---

## Installation

1. Place `VaultPlugin.jar` and `Vault.jar` (Vault API) in your server's `plugins` folder.
2. Start the server to generate the default `config.yml`.
3. Configure vault costs in `plugins/VaultPlugin/config.yml` if desired.
4. Make sure you have an economy plugin connected to Vault (like EssentialsX, iConomy, etc.).
5. Use `/vault` in-game to open your vault selector.

---

## Configuration (`config.yml`)

Example `vault-costs` setup:

```yaml

# ========================================
# VaultPlugin Configuration
# ========================================

vault-costs:
  1: 10000
  2: 50000
  3: 150000

unlocked: {}

# =========
# Notes
# =========

Vault numbers are 1-based.

Players unlock vaults by paying the cost defined in the config.

unlocked section is managed automatically by the plugin.

# ===========
# Commands
# ===========

/vault – Opens the vault selector.

/vault reload – Reloads the plugin configuration (requires vault.reload permission).

# ============
# Permissions
# ============
Permission	Description	Default
vault.use	Allows a player to open vaults	true
vault.reload	Allows reloading the plugin config	op

# ============
# Gui Layout
# ============

Vault Selector: 54 slots

Outer border: white glass

Vault slots: rows 1–4, columns 1–7 (28 slots)

Locked vaults: red barrier

Exit button: red barrier in slot 49

Vault Storage: 27 slots per vault

Previous/Next page buttons in slots 18/26

Exit button in slot 22

# ========================================
# Development / Contribution
# ========================================

Fork the repository and submit pull requests for features or bug fixes.

Ensure compatibility with PaperMC 1.13+ and Java 17–21.

Use Maven for building:

mvn clean package

Include Vault as a dependency in your local Maven repository if building from source.

# ==========
# Licence
# ==========

MIT License – free to use and modify.

# ==========
# Author
# ==========

Cold – https://github.com/ColdShadowsz
