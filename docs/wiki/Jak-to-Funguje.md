# 🗺️ Jak To Funguje – Přehled Celého Systému

Tato strana vysvětluje kompletní herní logiku pluginu od instalace po finální odměnu.

---

## Velký Obrázek

```
      ┌─────────────────────────────────────────────────┐
      │              TURISTICKÝ DENÍK 2026              │
      │                                                  │
      │  Hráč prozkoumává mapu BasicLandu a sbírá        │
      │  turistické zna mky na reálných herních místech. │
      │                                                  │
      │  Existují 3 způsoby jak získat zna mku:          │
      │   1. Navštívit fyzickou lokaci ve světě          │
      │   2. Sebrat limitovaný airdrop z nebe             │
      │   3. Admin zadá /turista give (pro NPC, questy)  │
      └─────────────────────────────────────────────────┘
```

---

## Způsob 1 – Fyzická Lokace (Proximity)

Toto je primární způsob sbírání běžných zna mek.

```
Admin nastaví souřadnice v config.yml nebo přes /turista setlocation
          │
          ▼
LocationManager každé 2s kontroluje online hráče
          │
          ├─── Hráč je v 3× radius → ActionBar hint: "Jsi blízko! (Xm)"
          │
          └─── Hráč vstoupí do radius →
                    │
                    ├── Má zna mku? → nic
                    │
                    └── Nemá zna mku? →
                              ├── Zapíše do SQLite (async)
                              ├── Pošle hráči zprávu
                              ├── Přehraje zvuk + particles
                              ├── Broadcastuje "první objeření"
                              ├── Zkontroluje milníky
                              └── Zkontroluje server-first dokončení
```

**Klíčové vlastnosti:**
- ✅ Kontrola každé 2 sekundy (ne na každý pohyb) – minimální lag
- ✅ Výpočet vzdálenosti je 2D (ignoruje Y) – funguje i na kopcích
- ✅ Hint se zobrazuje jako ActionBar (ne do chatu) – neobtěžuje
- ✅ Hint má 30s cooldown – nespamuje

---

## Způsob 2 – Airdrop (Limitovaná Edice)

```
config.yml definuje spawn_time a souřadnice airdrops
          │
AirdropManager každou minutu kontroluje časy
          │
Nastal spawn_time? →
          │
          ▼
world.dropItem() spawnuje fyzický item na souřadnicích
          │
          ├── Item označen PDC tagem "turistika:airdrop_id"
          │    └── Chrání před ClearLagem
          │
          ├── Kolem itemu létají TOTEM + END_ROD particles (každé 0,5s)
          │
          ├── Do chatu jde broadcast s lokací
          │
          └── Hráč si na item šáhne (EntityPickupItemEvent) →
                    │
                    ├── Event.setCancelled(true) – vždy!
                    │
                    ├── Zbývají pickup sloty? (max_pickups)
                    │    ├── NE → hráč dostane zprávu
                    │    └── ANO →
                    │              ├── Zapíše zna mku do SQLite
                    │              ├── Sníží slot counter v PDC
                    │              ├── Particles + zvuk z hráče
                    │              └── Zkontroluje milníky
                    │
                    └── Vypršel lifetime_minutes? →
                              ├── Thunder zvuk
                              ├── Explosion particles
                              └── item.remove()
```

---

## Způsob 3 – Ruční Udělení Adminem

```
/turista give <hráč> <id_zna mky>
          │
          ├── hasStamp() async check →
          │    └── Má? → Chyba adminovi
          │
          └── Nemá? →
                    ├── addStamp() async write
                    ├── Zpráva hráči + adminovi
                    ├── handleFirstDiscovery()
                    └── checkMilestones()
```

Tento způsob je vhodný jako **backend** pro:
- NPCs (Citizens, FancyNPCs) – NPC kliknutím volá příkaz
- Klikací bloky (CommandBlock)
- Questy (BetonQuest, QuestCreator)

---

## Turistický Deník – GUI

```
Hráč napíše /denik
          │
DenikCommand spustí async getUnlockedStamps()
          │
Po načtení → openInventory() na hlavním vlákně
          │
DenikGUI renderuje 54-slotový inventář:

  ┌──────────────────────────────────────────┐
  │ [✦] [✦] [✦] [?] [?] [⏳] [?] [?] [?]  │ ← strana 1 (max 45 zna mek)
  │ [?] [?] [?] [?] [?] [?] [?] [?] [?]   │
  │ ...                                      │
  │ [←] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [→]    │ ← strankovací řádek
  └──────────────────────────────────────────┘

  [✦] = nalezená zna mka (originální vizuál + lore)
  [?] = nenalezená (MAP item + "Neobjevená Zna mka")
  [⏳] = časově zamčená (CLOCK item + datum odemčení)
```

**Thread-safety GUI:**
- Async: načtení dat z DB
- Sync (main thread): vše co se týká Bukkit inventáře
- InventoryClickEvent + InventoryDragEvent → vždy cancelled

---

## Milníky a Server-First

```
Po každém přidání zna mky se volá:
          │
MilestoneManager.checkMilestones(player, totalCount)
          │
          ├── Existuje v config.yml klíč milestones.<totalCount>?
          │    └── ANO → spustí konzolové příkazy (give, lp, broadcast...)
          │
          └── totalCount >= total_stamps_for_completion?
                    └── ANO → ServerFirstManager.handleCompletion(player)
                              │
                              ├── registerMasterFirst() async →
                              │    └── INSERT OR IGNORE INTO master_firsts
                              │
                              └── rank 1, 2 nebo 3? →
                                        └── Spustí server_first_rewards.<rank>.commands
```

---

## Hologramy a Žebříček

```
Admin zadá /turista hologram spawn
          │
HologramManager.createHologram(location, id)
          │
          ├── Spawnuje TextDisplay entitu (Paper 1.20+)
          │    ├── Billboard.CENTER (vždy otočen k hráči)
          │    └── Persistent = true
          │
          ├── Uloží UUID + lokaci do SQLite
          │
          └── Každé 2 minuty BukkitRunnable:
                    ├── getTopPlayers(10) async
                    └── td.setText(žebříček) na main threadu

Po restartu serveru:
          ├── loadFromDatabase() načte hologram záznamy
          ├── Hledá existující TextDisplay entity podle UUID
          └── Pokud nenajde → spawné novou na uložených souřadnicích
```

---

## Databáze – Přehled Tabulek

| Tabulka | Obsah |
|---|---|
| `unlocked_stamps` | (uuid, stamp_id) – kdo má jakou zna mku |
| `server_firsts` | (stamp_id, uuid, player_name) – kdo první objevil zna mku |
| `master_firsts` | (rank, uuid, player_name) – kdo 1./2./3. dokončil celý deník |
| `holograms` | (id, world, x, y, z, entity_uuid) – uložené hologramy |

Všechny operace běží přes `Executors.newSingleThreadExecutor()` + WAL mode.
