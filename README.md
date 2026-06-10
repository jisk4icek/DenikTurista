<div align="center">

<img src="https://img.shields.io/badge/PaperMC-1.20%2B-blue?style=for-the-badge&logo=data:image/svg%2Bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0id2hpdGUiIGQ9Ik0xMiAyQzYuNDggMiAyIDYuNDggMiAxMnM0LjQ4IDEwIDEwIDEwIDEwLTQuNDggMTAtMTBTMTcuNTIgMiAxMiAyem0wIDE4Yy00LjQxIDAtOC0zLjU5LTgtOHMzLjU5LTggOC04IDggMy41OSA4IDgtMy41OSA4LTggOHoiLz48L3N2Zz4="/>
<img src="https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge&logo=openjdk"/>
<img src="https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven"/>
<img src="https://img.shields.io/badge/SQLite-Database-003B57?style=for-the-badge&logo=sqlite"/>
<img src="https://img.shields.io/badge/Dependencies-ZERO-brightgreen?style=for-the-badge"/>
<img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge"/>

# 🗺️ BasicLand Turistika

**Prémiový PaperMC plugin pro letní turistický event.**  
Hráči sbírají skryté turistické známky, závodí o žebříček a loví limitované airdropy.

[📖 Wiki](../../wiki) · [🐛 Issues](../../issues) · [💬 Discord](#) · [📦 Releases](../../releases)

---

</div>

## ✨ Funkce

| Kategorie | Funkce |
|---|---|
| 🎒 **Deník** | GUI inventář se stránkováním, vizuální rozlišení nalezených, uzamčených a neobjevených známek |
| ⏰ **Časové zámky** | Každá známka má volitelné `unlock_date` – do té doby se zobrazí jen s datem odemčení |
| 🏆 **Milníky** | Automatické odměny za nasbírání N známek přes libovolné konzolové příkazy |
| 🥇 **Server-First** | Grandiózní odměny + tituly pro 1., 2. a 3. hráče, kteří jako první dokončí celý deník |
| 📡 **Broadcast obječní** | Každá úplně první nalezená známka je slavnostně ohlášena do celoserverového chatu |
| 🪂 **Airdropy** | Fyzický předmět s particles spadne v určitý čas, chráněn PDC před ClearLagem |
| 🪧 **Hologramy** | TextDisplay entity zobrazující živý TOP žebříček na spawnu, persistence přes SQLite |
| 📋 **Admin příkazy** | Give, info, list, top, reload, hologram spawn/remove – vše s tab-doplňováním |
| 🗄️ **SQLite + Thread-safe** | Jednovláknový ExecutorService, WAL režim, nulová ztráta dat |
| 🔧 **Zero Dependencies** | Žádné externí knihovny, žádný shade, plug & play |

---

## 🚀 Instalace

```bash
# 1. Stáhni nejnovější .jar z Releases
# 2. Vlož do složky /plugins/ na serveru (Paper 1.20+)
# 3. Restartuj server
# 4. Uprav plugins/BasicLandTuristika/config.yml
# 5. Uprav plugins/BasicLandTuristika/messages.yml
```

> [!IMPORTANT]
> Plugin vyžaduje **PaperMC 1.20** nebo novější. Spigot není podporován (TextDisplay entity jsou Paper-specifické).

---

## 📋 Příkazy

### `/denik` — Turistický deník hráče
Otevře GUI inventář s přehledem všech známek.

### `/turista <sub-příkaz>` — Admin správa
Vyžaduje oprávnění `turista.admin` (default: OP).

| Příkaz | Popis |
|---|---|
| `/turista give <hráč> <id>` | Udělí hráči turistickou známku |
| `/turista top` | Zobrazí TOP 10 hráčů v chatu |
| `/turista list` | Vypíše všechny známky z config.yml |
| `/turista info <hráč>` | Zobrazí progres konkrétního hráče |
| `/turista hologram spawn` | Spawnuje TextDisplay leaderboard hologram |
| `/turista hologram remove` | Odstraní hologram |
| `/turista reload` | Znovunačte config.yml i messages.yml |

---

## ⚙️ Konfigurace

Konfigurace se nachází v `plugins/BasicLandTuristika/config.yml`.  
Podrobný popis všech klíčů viz **[📖 Wiki → Konfigurace](../../wiki/Konfigurace)**.

### Ukázka – přidání nové známky
```yaml
stamps:
  moje_nova_znamka:
    name: "&aZnámka: Krásná Hora"
    lore:
      - "&7Výhled z vrcholu byl úchvatný!"
    material: PAPER
    custom_model_data: 1005
    # Volitelné – datum odemčení:
    unlock_date: "2026-08-01 00:00:00"
```

### Ukázka – nastavení airdrops
```yaml
airdrops:
  letni_drop_01:
    stamp_id: "podzimni_special"
    spawn_time: "2026-07-15 20:00:00"
    world: "world"
    x: 0.5
    y: 65.0
    z: 0.5
    max_pickups: 3
    lifetime_minutes: 60
```

---

## 🏗️ Architektura projektu

```
src/main/java/cz/basicland/turistika/
├── BasicLandTuristika.java         # Hlavní třída, lifecycle
├── command/
│   ├── DenikCommand.java           # /denik
│   └── TuristaCommand.java         # /turista (+ TabCompleter)
├── config/
│   ├── ConfigManager.java          # Načítání config.yml, StampData
│   └── MessageManager.java         # Načítání messages.yml
├── database/
│   └── DatabaseManager.java        # SQLite, single-thread executor, WAL
├── gui/
│   ├── Gui.java                    # Abstraktní GUI základna
│   ├── GuiManager.java             # Click + Drag listener
│   └── DenikGUI.java               # Inventář deníku
├── mechanics/
│   ├── MilestoneManager.java       # Milníky + konzolové odměny
│   ├── ServerFirstManager.java     # Server-first dokončení deníku
│   ├── AirdropManager.java         # Airdropy, PDC ochrana, EntityPickup
│   └── HologramManager.java        # TextDisplay hologramy, persistence
└── utils/
    ├── ItemBuilder.java             # Fluent builder pro ItemStack
    └── ChatUtil.java               # Premium vizuál chat zpráv
```

---

## 🛡️ Bezpečnost & Anti-Cheat

### Ochrana airdrop itemů před ClearLagem
Každý airdrop item je označen pomocí **PersistentDataContainer (PDC)**:
```java
pdc.set(new NamespacedKey("turistika", "airdrop_id"), PersistentDataType.STRING, stampId);
```
ClearLag a podobné pluginy **nemohou** smazat item s takovým PDC tagem, pokud je správně nakonfigurovaný whitelist. Viz [Wiki → Anti-ClearLag](../../wiki/Anti-ClearLag).

### Thread-Safety SQLite
Database operace běží výhradně přes `Executors.newSingleThreadExecutor()` – žádné souběžné zápisy, nulová korupce dat.

### GUI bezpečnost
`InventoryDragEvent` je zachycen a zrušen, aby hráči nemohli tažením vložit vlastní itemy do GUI menu.

---

## 🗺️ Roadmap

| Fáze | Stav | Popis |
|---|---|---|
| **Fáze 1** | ✅ Hotovo | SQLite, /denik GUI, /turista give, Config/Messages systém |
| **Fáze 2** | ✅ Hotovo | Časové zámky, milníky, server-first odměny |
| **Fáze 3** | ✅ Hotovo | Airdropy s PDC ochranou, particles, EntityPickup |
| **Fáze 4** | ✅ Hotovo | TextDisplay hologramy, /turista top, /turista reload |
| **Fáze 5** | 📋 Plánováno | Anti-Xray integrace, Suspicious-block quest systém |
| **Fáze 6** | 📋 Plánováno | PlaceholderAPI podpora (`%turistika_stamps%`) |
| **Fáze 7** | 💡 Nápad | Webová správa přes REST API endpoint |

---

## 🔨 Sestavení ze zdrojového kódu

```bash
git clone https://github.com/jisk4icek/DenikTurista.git
cd DenikTurista
mvn clean package
# Výsledný JAR: target/BasicLandTuristika-1.0.0.jar
```

**Požadavky:**
- Java 17+
- Maven 3.8+
- Internetové připojení (stažení Paper API při prvním buildu)

---

## 📜 Licence

Tento plugin je licencován pod **MIT licencí**. Viz soubor [LICENSE](LICENSE).

---

<div align="center">

Vyvinut s ❤️ pro komunitu **BasicLand**

</div>
