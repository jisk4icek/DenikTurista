# 🗺️ Roadmap – Plánované Fáze

Přehled aktuálního stavu a budoucích plánů pro BasicLand Turistika.

---

## Dokončené fáze

### ✅ Fáze 1 – Jádro a Základní Systém
- [x] Maven projekt s Paper API 1.20+
- [x] SQLite databáze s jednovláknovým executorem (thread-safe)
- [x] Vlastní GUI API nad Inventory (bez externích závislostí)
- [x] Příkaz `/denik` s plně konfigurovatelným GUI a stránkováním
- [x] Příkaz `/turista give <hráč> <id>` pro administrátory
- [x] `config.yml` a `messages.yml` s hot-reload podporou

### ✅ Fáze 2 – Časové Zámky a Milníky
- [x] Systém `unlock_date` s Java Time API (thread-safe)
- [x] GUI vizuál pro uzamčené a neobjevené známky
- [x] Systém milníků s konzolových odměn
- [x] Server-First systém (1., 2., 3. hráč s dokončeným deníkem)
- [x] Broadcast prvního objeření každé známky na serveru

### ✅ Fáze 3 – Airdropy a Limitované Edice
- [x] BukkitRunnable plánovač s minutovou kontrolou airdropů
- [x] Fyzický item spawnovaný přes `world.dropItem()`
- [x] PDC ochrana proti ClearLagu (`turistika:airdrop_id`)
- [x] EntityPickupItemEvent listener s vlastní logikou
- [x] Particle efekty (TOTEM, END_ROD) a zvukové efekty
- [x] Omezený počet pickupů (`max_pickups`)
- [x] Automatický expirační systém s majestátním zvukem

### ✅ Fáze 4 – Hologramy a Admin Příkazy
- [x] TextDisplay hologramy (Paper 1.20+, bez ArmorStandů)
- [x] Persistentní uložení hologramů v SQLite
- [x] Automatická obnova po restartu serveru
- [x] Periodická aktualizace obsahu (TOP hráči)
- [x] `/turista top` – textový žebříček v chatu
- [x] `/turista list` – přehled všech známek
- [x] `/turista info <hráč>` – progres hráče
- [x] `/turista reload` – hot reload konfigurace
- [x] TabCompleter pro všechny sub-příkazy
- [x] ChatUtil třída pro premium vizuál zpráv

---

## Plánované fáze

### 📋 Fáze 5 – Anti-Cheat & Quest Systém
- [ ] Integrace s Paper Anti-Xray pro skrývání "hint bloků"
- [ ] Interaktivní nápovědní bloky (`suspicious_sand`, `suspicious_gravel`)
- [ ] Systém fyzických lokací – admin může označit blok jako místo kde je knowńka
- [ ] Ochrana airdrop lokací před griefingem (WorldGuard integrace)
- [ ] Detekce a blokování duplicitního sbírání přes alternativní účty

### 📋 Fáze 6 – PlaceholderAPI Integrace
- [ ] Registrace vlastních placeholderů:
  - `%turistika_stamps%` – počet nasbíraných zna′mek hráče
  - `%turistika_rank%` – pořadí hráče v žebříčku
  - `%turistika_total%` – celkový počet zna′mek na serveru
  - `%turistika_completion%` – procento dokončení deníku
- [ ] Podpora v scoreboard pluginech (TAB, FeatherBoard)
- [ ] Podpora v chat formátech (EssentialsX, ChatControl)

### 💡 Fáze 7 – Rozšířené Admin Nástroje
- [ ] Webová správa přes zabudovaný REST API server (HTTP)
- [ ] Export statistik do CSV/JSON souboru
- [ ] Admin GUI pro správu zna′mek a airdropů přímo v inventáři
- [ ] Statistická mapa aktivity hráčů (heatmap)
- [ ] Podpora více instancí eventu najednou (paralelní "sezonní" deníky)

### 💡 Fáze 8 – Vizuální Upgrade
- [ ] Animované hologramy (střídání řádků, blikání)
- [ ] Boss bar progress bar pro aktuálně probíhající airdrop
- [ ] Action bar notifikace při přiblížení k airdrop lokaci
- [ ] Custom Sound API pro vlastní zvuky ze resource packu

---

## Jak přispět

Máš nápad nebo narazil jsi na chybu? Otevři [Issue](../../issues) nebo přispěj přes [Pull Request](../../pulls).

Viz [🔨 Sestavení ze Zdrojáku](Build-ze-Zdrojaku) pro instrukce k lokálnímu vývojovém prostředí.
