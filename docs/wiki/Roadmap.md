# 🗺️ Roadmap – Plánované Fáze

Přehled aktuálního stavu a budoucích plánů pro BasicLand Turistika.

---

## ✅ Dokončeno

### Fáze 1 – Jádro a Základní Systém
- [x] Maven projekt, Paper API 1.20+, plugin.yml
- [x] SQLite databáze – jednovláknový executor, WAL mode, PRAGMA optimalizace
- [x] Vlastní GUI API (bez externích závislostí, InventoryHolder pattern)
- [x] `/denik` GUI s stránkováním (45 zna mek na stránku)
- [x] `/turista give <hráč> <id>` pro administrátory
- [x] `config.yml` a `messages.yml` s plnou lokalizací

### Fáze 2 – Časové Zámky a Milníky
- [x] `unlock_date` systém s Java Time API (thread-safe `DateTimeFormatter`)
- [x] Vizuální odlišení zamčených / neobjevených / nalezených zna mek v GUI
- [x] Milníkový systém – konzolové příkazy při N zna mkách
- [x] Server-First systém – 3 výhercovská místa, trvalé uložení v SQLite
- [x] Broadcast při prvním objeření každé zna mky na serveru

### Fáze 3 – Airdropy a Limitované Edice
- [x] `BukkitRunnable` plánovač s minutovou kontrolou
- [x] Fyzický item přes `world.dropItem()` s nastavitelnou lokací
- [x] **PDC ochrana** (`turistika:airdrop_id`) – imunita vůči ClearLagu
- [x] `EntityPickupItemEvent` s plnou vlastní logikou
- [x] Particle efekty (TOTEM, END_ROD) každých 0,5s
- [x] Omezený počet pickupů (`max_pickups`)
- [x] Expirační systém – thunder zvuk + explosion particles
- [x] Broadcast s lokací při spawnu airdrops

### Fáze 4 – Hologramy a Admin Příkazy
- [x] `TextDisplay` hologramy (Paper 1.20+, **bez ArmorStandů**)
- [x] Persistentní uložení UUID hologramů v SQLite
- [x] Automatická obnova hologramů po restartu serveru
- [x] Periodická aktualizace obsahu každé 2 minuty
- [x] `/turista top` – stylizovaný žebříček v chatu
- [x] `/turista list` – přehled zna mek s indikátory
- [x] `/turista info <hráč>` – progres s vizuálním progress barem
- [x] `/turista reload` – hot reload konfigurace
- [x] `TabCompleter` pro všechny sub-příkazy
- [x] `ChatUtil` třída pro premium vizuál zpráv

### Fáze 4.5 – Fyzické Lokace (Proximity Systém)
- [x] `LocationManager` – timer-based check každé 2 sekundy
- [x] 2D vzdálenostní výpočet (ignoruje Y pro hory a patra)
- [x] ActionBar hint při přiblížení (3× radius) s 30s cooldownem
- [x] `/turista setlocation <id> [radius]` – nastav lokaci na svém místě
- [x] `/turista removelocation <id>` – odeber lokaci
- [x] `/turista locations` – přehled všech lokací se souřadnicemi
- [x] Automatický zápis souřadnic do config.yml přes příkaz
- [x] Particle efekty při získání zna mky na lokaci (FIREWORK + HAPPY_VILLAGER)

---

## 📋 Plánované Fáze

### Fáze 5 – Anti-Cheat & Fyzická Nápověda
- [ ] Integrace s Paper Anti-Xray pro skrytí "hint bloků" poblíž lokací
- [ ] Systém interaktivních nápovědních bloků (right-click `suspicious_sand`)
- [ ] **Anti-Alt ochrana** – detekce duplicitních účtů na stejné IP
- [ ] WorldGuard integrace – automatické zamknutí oblastí kolem lokací
- [ ] Konfigurovatelná cool-down perioda (hráč nemůže znovu navštívit lokaci po X minutách)
- [ ] Debug příkaz `/turista debug <hráč>` pro výpis detailů o proximity

### Fáze 6 – PlaceholderAPI Integrace
- [ ] Registrace pluginu jako PAPI expanzionů
- [ ] `%turistika_stamps%` – počet nasbíraných zna mek
- [ ] `%turistika_total%` – celkový počet zna mek v eventu
- [ ] `%turistika_rank%` – pořadí v globálním žebříčku
- [ ] `%turistika_completion%` – procento dokončení (0–100)
- [ ] `%turistika_next_milestone%` – do dalšího milníku zbývá N zna mek
- [ ] Podpora v TAB, FeatherBoard, EssentialsX chat

### Fáze 7 – Rozšířené Admin Nástroje
- [ ] Webová správa přes zabudovaný HTTP server (REST API)
- [ ] Export statistik do CSV / JSON souboru
- [ ] Admin GUI `/turista admingui` – správa bez psaní příkazů
- [ ] Statistická heatmapa aktivity hráčů (textový výstup)
- [ ] Podpora více paralelních eventů (sezónní deníky `--event=leto2026`)

### Fáze 8 – Vizuální & Zvukový Upgrade
- [ ] Animované hologramy (střídání řádků, blikání textu)
- [ ] BossBar progress při aktuálně probíhajícím airdrops
- [ ] Konfigurovatelné particle efekty pro každou zna mku zvlášť
- [ ] Zvukový profil pro každou zna mku (custom sounds z resource packu)
- [ ] Cinematic kamera efekt při získání zna mky (sekvence titulků)

---

## 💡 Nápady Komunity

Máš nápad, co přidat? Otevři [Discussion](../../discussions) nebo [Issue](../../issues) s tagem `enhancement`.
