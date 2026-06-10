<div align="center">

<img src="https://img.shields.io/badge/PaperMC-1.20%2B-blue?style=for-the-badge"/>
<img src="https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge&logo=openjdk"/>
<img src="https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven"/>
<img src="https://img.shields.io/badge/SQLite-Database-003B57?style=for-the-badge&logo=sqlite"/>
<img src="https://img.shields.io/badge/Dependencies-ZERO-brightgreen?style=for-the-badge"/>
<img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge"/>

# 🗺️ BasicLand Turistika

**Prémiový PaperMC plugin pro letní turistický event.**  
Hráči sbírají skryté turistické zna mky, závodí o žebříček, loví limitované airdropy a budují denní streak!

[📖 Wiki](../../wiki) · [🐛 Issues](../../issues) · [📦 Releases](../../releases)

---

</div>

## ✨ Funkce

| Kategorie | Funkce |
|---|---|
| 🎒 **Deník** | GUI inventář se stránkováním (45 zna mek/strana), vizuální rozlišení stavů |
| 📍 **Fyzické Lokace** | Hráč přijde na místo a zna mku dostane automaticky (proximity, 2s check) |
| ⏰ **Časové Zámky** | `unlock_date` – zna mky dostupné až od nastaveného data/času |
| 🏆 **Milníky** | Automatické odměny za N zna mek (items, peníze, ranky, title, broadcast) |
| 🔥 **Streak Systém** | Odměny za po sobě jdoucí dny aktivity (3, 7, 14, 30 dní...) |
| 🥇 **Server-First** | Dramatické odměny pro 1./2./3. hráče kteří dokončí celý deník |
| 📡 **Broadcast Objeření** | Každá první nalezená zna mka = celoserverové oznámení |
| 🪂 **Airdropy** | Fyzický item s particles padá v definovaný čas, chráněn PDC před ClearLagem |
| 🪧 **Hologramy** | TextDisplay entity zobrazující živý TOP žebříček, persistence přes SQLite |
| 📋 **Admin Příkazy** | give, info, list, top, setlocation, locations, hologram, reload – vše s tab-doplňováním |
| 🗄️ **SQLite + Thread-safe** | Single-thread executor, WAL režim, nulová ztráta dat |
| 🔧 **Zero Dependencies** | Žádné externí knihovny, žádný shade, plug & play |

---

## 🚀 Instalace

```bash
# 1. Stáhni nejnovější .jar z Releases
# 2. Vlož do složky /plugins/ (Paper 1.20+)
# 3. Restartuj server
# 4. Nastav config.yml, poté /turista reload
```

> [!IMPORTANT]
> Vyžaduje **PaperMC 1.20+**. Spigot není podporován.

---

## 📋 Příkazy

### `/denik` — Turistický deník hráče

### `/turista <sub-příkaz>` — Admin správa (`turista.admin`)

| Příkaz | Popis |
|---|---|
| `/turista give <hráč> <id>` | Udělí hráči turistickou zna mku |
| `/turista setlocation <id> [r]` | Nastav lokaci zna mky na své pozici (r = radius v metrech) |
| `/turista removelocation <id>` | Odstraní lokaci zna mky |
| `/turista locations` | Vypíše všechny lokace se souřadnicemi |
| `/turista top` | TOP 10 hráčů v chatu |
| `/turista list` | Výpis zna mek + indikátor lokace `[LOC]` / `[---]` |
| `/turista info <hráč>` | Progres hráče s vizuálním progress barem |
| `/turista hologram spawn` | Spawnuje TextDisplay leaderboard hologram |
| `/turista hologram remove` | Odstraní hologram |
| `/turista reload` | Reload config.yml + messages.yml + lokace |

---

## ⚙️ Konfigurace – Rychlý Přehled

### Přidání zna mky + fyzická lokace

```yaml
# config.yml
stamps:
  hrad_karlstejn:
    name: "&6✦ Hrad Karlštejn ✦"
    lore:
      - "&7Navštívil jsi ikonický hrad!"
    material: PAPER
    custom_model_data: 1001
    # Fyzická lokace – nastav příkazem /turista setlocation hrad_karlstejn 8
    location:
      world: "world"
      x: 150.0
      y: 68.0
      z: -300.0
      radius: 8.0           # Poloměr v metrech
```

### Typy Odměn v Milnících

```yaml
milestones:
  10:
    commands:
      - "give %player% diamond 10"              # Předměty
      - "eco give %player% 2000"               # Peníze (EssentialsX)
      - "lp user %player% parent add vip"      # LuckPerms rank
      - "lp user %player% meta setprefix 100 [&6Turista&r]"  # Prefix
      - "title %player% &6Zlatý milník!|&710 zna mek!"       # Title
      - "broadcast &e%player% &7má 10 zna mek!" # Chat broadcast
      - "crates key give %player% touristicky 1" # Crate klíč

streak_rewards:
  7:
    commands:
      - "give %player% diamond 2"
      - "title %player% &57 Dní Streak!|&7Průzkumník!"
```

---

## 🏗️ Architektura

```
src/main/java/cz/basicland/turistika/
├── BasicLandTuristika.java          # Hlavní třída, lifecycle
├── command/
│   ├── DenikCommand.java            # /denik
│   └── TuristaCommand.java          # /turista + TabCompleter
├── config/
│   ├── ConfigManager.java
│   └── MessageManager.java
├── database/
│   └── DatabaseManager.java         # SQLite, thread-safe executor, WAL
├── gui/
│   ├── Gui.java / GuiManager.java / DenikGUI.java
├── mechanics/
│   ├── AirdropManager.java          # Airdropy + PDC ochrana
│   ├── HologramManager.java         # TextDisplay hologramy
│   ├── LocationManager.java         # Proximity systém (fyzické lokace)
│   ├── MilestoneManager.java        # Milníky + odměny
│   ├── RewardManager.java           # Centrální odměny (title, broadcast, cmd)
│   ├── ServerFirstManager.java      # Server-first dokončení
│   └── StreakManager.java           # Denní streak systém
└── utils/
    ├── ChatUtil.java                 # Premium vizuál chat zpráv
    └── ItemBuilder.java
```

---

## 🗺️ Roadmap

| Fáze | Stav | Popis |
|---|---|---|
| **Fáze 1** | ✅ | SQLite, /denik GUI, /turista give, Config/Messages |
| **Fáze 2** | ✅ | Časové zámky, milníky, server-first odměny |
| **Fáze 3** | ✅ | Airdropy s PDC ochranou, particles, EntityPickup |
| **Fáze 4** | ✅ | TextDisplay hologramy, admin příkazy, TabCompleter |
| **Fáze 4.5** | ✅ | Proximity systém (setlocation, radius, ActionBar hint) |
| **Fáze 5** | ✅ | Streak systém, RewardManager (title, broadcast, cmds) |
| **Fáze 6** | 📋 | Anti-Cheat integrace, WorldGuard, suspicious blocks |
| **Fáze 7** | 📋 | PlaceholderAPI podpora |
| **Fáze 8** | 💡 | BossBar, animované hologramy, sezónní deníky |

---

## 🛡️ Bezpečnost

- **PDC ochrana airdropů** – imunní vůči ClearLagu
- **Thread-safe SQLite** – `newSingleThreadExecutor()` + WAL
- **GUI ochrana** – `InventoryClickEvent` + `InventoryDragEvent` (anti-exploit)
- **Paper Anti-Xray** – konfigurace viz [Wiki](../../wiki/Anti-ClearLag-a-Anti-Cheat)

---

## 🔨 Build ze Zdrojáku

```bash
git clone https://github.com/jisk4icek/DenikTurista.git
cd DenikTurista
mvn clean package
# → target/BasicLandTuristika-1.0.0.jar
```

---

## 📜 Licence

MIT License – viz [LICENSE](LICENSE)

---

<div align="center">

## 📖 Kompletní Dokumentace

**Wiki je dostupná v [`docs/wiki/`](docs/wiki/) a na [GitHub Wiki](../../wiki).**

> **Aktivace GitHub Wiki:** Na GitHubu přejdi na záložku **Wiki**, klikni **"Create the first page"**,  
> ulož stránku. Poté spusť lokálně: `cd wiki_deploy && git push --force origin master`  
> a všechny stránky se nahrají automaticky.

---

Vyvinut s ❤️ pro komunitu **BasicLand**

</div>
