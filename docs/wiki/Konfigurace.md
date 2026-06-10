# ⚙️ Konfigurace

Plugin používá dva konfigurační soubory generované automaticky při prvním spuštění.

---

## `config.yml`

Nachází se v: `plugins/BasicLandTuristika/config.yml`

### Sekce `stamps` – Turistické Známky

```yaml
stamps:
  <unikatni_id>:
    name: "&6Název Známky"        # Zobrazovaný název (podporuje & barvy)
    lore:                          # Popis pod názvem (seznam řádků)
      - "&7Řádek 1"
      - "&7Řádek 2"
    material: PAPER                # Material z Bukkit API (např. PAPER, GOLD_NUGGET)
    custom_model_data: 1001        # Volitelné – ID pro resource pack modely
    unlock_date: "yyyy-MM-dd HH:mm:ss"  # Volitelné – datum odemčení
```

**Příklad kompletní známky:**
```yaml
stamps:
  navsteva_spawnu:
    name: "&e✦ Turistická Známka: Spawn BasicLand ✦"
    lore:
      - "&7Dokazuje, že jsi navštívil spawn"
      - "&7BasicLandu během Letního Eventu 2026."
      - ""
      - "&8Série: &eLéto 2026"
    material: PAPER
    custom_model_data: 1001
    unlock_date: "2026-06-15 12:00:00"
```

> [!TIP]
> ID známky (klíč pod `stamps:`) může obsahovat jen malá písmena, číslice a podtržítka. Toto ID se používá v příkazu `/turista give <hráč> <ID>`.

---

### Sekce `airdrops` – Airdropy

```yaml
airdrops:
  <unikatni_id_airdrops>:
    stamp_id: "id_znamky"          # ID existující známky ze sekce stamps
    spawn_time: "yyyy-MM-dd HH:mm:ss"  # Přesný čas spawnu
    world: "world"                 # Název světa (musí existovat na serveru)
    x: 0.5                         # Souřadnice X
    y: 65.0                        # Souřadnice Y
    z: 0.5                         # Souřadnice Z
    max_pickups: 3                 # Kolik hráčů může airdrop sebrat
    lifetime_minutes: 60           # Jak dlouho leží na zemi (pak zmizí)
```

---

### Sekce `gui` – Vzhled deníku

```yaml
gui:
  title: "&8✦ &bTuristický Deník &8✦"   # Název inventáře

  unknown_stamp:      # Předmět pro NEOBJEVENOU (ale odemčenou) známku
    material: MAP
    name: "&c&l? Neobjevená Známka ?"
    lore: [...]

  locked_stamp:       # Předmět pro ČASOVĚ ZAMČENOU známku
    material: CLOCK
    name: "&8&l⏳ Uzamčená Edice"
    lore:
      - "&7Odemkne se: &e{unlock_date}"   # Placeholder – automaticky nahrazen
```

---

### Sekce `milestones` – Milníky

```yaml
milestones:
  5:                  # Číslo = počet získaných známek
    commands:
      - "give %player% diamond 3"        # %player% = jméno hráče
      - "broadcast Zpráva pro celý server"
  10:
    commands:
      - "lp user %player% parent add vip"
```

---

### Sekce `server_first_rewards` – Server-First

```yaml
total_stamps_for_completion: 10    # Kolik známek = kompletní deník

server_first_rewards:
  1:                  # Rank 1 = první na serveru
    commands:
      - "lp user %player% parent add turistamaster"
  2:
    commands:
      - "give %player% diamond_block 5"
  3:
    commands:
      - "give %player% emerald_block 5"
```

---

## `messages.yml`

Nachází se v: `plugins/BasicLandTuristika/messages.yml`

Tento soubor obsahuje **všechny texty**, které plugin posílá hráčům.

| Klíč | Popis | Placeholders |
|---|---|---|
| `prefix` | Prefix před každou zprávou | – |
| `no_permission` | Zpráva při nedostatku oprávnění | – |
| `player_only` | Příkaz jen pro hráče | – |
| `stamp_received` | Hráč dostal známku | `{stamp_name}` |
| `stamp_already_owned` | Hráč už má tuto známku | – |
| `stamp_given` | Admin dal hráči známku | `{stamp_id}`, `{player}` |
| `stamp_not_found` | Neexistující ID známky | `{stamp_id}` |
| `player_not_found` | Hráč není online | `{player}` |
| `first_discovery` | Broadcast prvního objeření | `{player}`, `{stamp_name}` |
| `gui_previous_page` | Text tlačítka předchozí strana | – |
| `gui_next_page` | Text tlačítka další strana | – |

> [!NOTE]
> Po každé úpravě souborů použij `/turista reload` – **není potřeba restart serveru**.
