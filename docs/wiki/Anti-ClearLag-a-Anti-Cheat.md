# 🛡️ Anti-XRay, Anti-Cheat & Zabezpečení

Kompletní průvodce zabezpečením turistického eventu na Production serveru.

---

## 1. Paper Anti-Xray (Vestavěný)

Paper má od verze 1.19+ výkonný vestavěný Anti-Xray, který stačí správně nakonfigurovat.

### Kde nastavit
Soubor: `config/paper-world-defaults.yml`

### Doporučená konfigurace

```yaml
anticheat:
  anti-xray:
    enabled: true

    # Režim 2 je nejsilnější – vyplní chunk falešnými bloky
    # Hráč s X-Ray viděl chaos místo skutečných bloků
    engine-mode: 2

    # Do jaké hloubky se aplikuje (výška 64 = level moře)
    max-block-height: 64

    # Radius kolem hráče, kde se falešné bloky odstraní (2 = výchozí)
    update-radius: 2

    # Skryté bloky = bloky, které se nahradí falešnými v engine-mode 2
    hidden-blocks:
      - copper_ore
      - deepslate_copper_ore
      - gold_ore
      - deepslate_gold_ore
      - iron_ore
      - deepslate_iron_ore
      - coal_ore
      - deepslate_coal_ore
      - lapis_ore
      - deepslate_lapis_ore
      - diamond_ore
      - deepslate_diamond_ore
      - emerald_ore
      - deepslate_emerald_ore
      - redstone_ore
      - deepslate_redstone_ore
      - ancient_debris
      # ↓ PŘIDEJ BLOKY, KTERÉ POUŽÍVÁŠ JAKO "HINT" KE ZNAMKÁM
      # Například skrytá truhla nebo dekorace poblíž lokace zna mky
      - mossy_cobblestone
      - chiseled_stone_bricks
      - cracked_stone_bricks

    # Náhradní bloky (čím se hidden-blocks nahradí)
    replacement-blocks:
      - stone
      - deepslate
      - oak_planks
```

> [!IMPORTANT]
> Engine-mode 2 způsobuje vyšší server load při generování chunkú. Pokud máš slabý server, použij engine-mode 1 (méně efektivní, ale méně náročný).

---

## 2. Ochrana Lokací Zna mek – WorldGuard

Instaluj **WorldGuard** pro ochranu oblastí, kde jsou turistické lokace.

### Ochrana oblasti kolem zna mky

```bash
# 1. Označ oblast (WorldEdit wand)
//wand
# Označ rohy oblasti (kde je zna mka)

# 2. Vytvoř region
/region define znamka_karlstejn

# 3. Nastav pravidla
/region flag znamka_karlstejn pvp deny
/region flag znamka_karlstejn block-break deny
/region flag znamka_karlstejn block-place deny
/region flag znamka_karlstejn chest-access deny

# Volitelně – zakáže průlet elytr do oblasti (hráč musí přijít pěšky)
/region flag znamka_karlstejn elytra deny
```

### Pro VIP oblasti (pouze s určitým rankem)

```bash
/region flag znamka_special entry deny
/region addmember znamka_special g:vip
```

---

## 3. Ochrana Airdropů před ClearLagem

### Jak to plugin řeší interně

Každý airdrop item obsahuje PDC tag:
```
Namespace: turistika
Key: airdrop_id
Type: STRING
Value: <id_zna mky>
```

### Konfigurace pro různé Anti-Lag pluginy

#### ClearLag (starší verze)
ClearLag bohužel **nepodporuje** PDC whitelist nativně. Řešení:

```yaml
# clearlag/config.yml
entity-clear:
  enabled: true
  worlds:
    '*':
      enabled: true
      # Toto nefunguje u ClearLag pro PDC - viz alternativy níže
```

**Doporučujeme místo ClearLagu použít:**

#### Paper Vlastní Entity Limity
```yaml
# paper-world-defaults.yml
entities:
  spawning:
    monster-spawn-max-light-level: -1
  max-auto-save-chunks-per-tick: 8

# Snižte počet entit jinak – přes spawn limitery, ne ClearLag
```

#### EntityCleaner / RedstoneTools (mají PDC whitelist)
Tyto moderní alternativy umí filtrovat entity podle PDC tagů:
```yaml
# entitycleaner config (příklad)
item-cleanup:
  enabled: true
  interval: 300
  whitelist-pdc:
    - "turistika:airdrop_id"  # Naše airdropy NEMAZAT
```

---

## 4. Anti-Cheat pro Sbírání Zna mek

### Ochrana Proximity Systému

Hráč by mohl použít **Fly/Speed Hack** pro rychlé projet všechny lokace.

**Doporučené Anti-Cheat pluginy:**
| Plugin | Platba | Detekce |
|---|---|---|
| **Grim AntiCheat** | Free (GitHub) | Pohyb, fly, speed, nofall |
| **Matrix AntiCheat** | Premium | Komplexní detekce |
| **Spartan** | Premium | Pohyb + combat |

Jakýkoliv Anti-Cheat, který blokuje ilegální pohyb, **automaticky** chrání i proximity systém.

### Rate Limiting v Pluginu

LocationManager má vestavěný throttling:
- Kontrola každé **2 sekundy** (ne na každý tick)
- Hint cooldown **30 sekund** per hráč per zna mka
- Zna mka se přidá **jednou** (INSERT OR IGNORE v SQLite)

Hráč tedy nemůže získat stejnou zna mku vícekrát ani rychlým pohybem.

---

## 5. Ochrana GUI před Exploity

Plugin řeší dva klasické inventory exploity:

### Item Duplication přes Drag
```java
@EventHandler
public void onInventoryDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder() instanceof Gui) {
        event.setCancelled(true); // Blokuje drag
    }
}
```

### Click-through Exploit (Shift+Click)
```java
@EventHandler
public void onInventoryClick(InventoryClickEvent event) {
    if (event.getInventory().getHolder() instanceof Gui) {
        event.setCancelled(true); // Blokuje všechna kliknutí
        // ... pak zpracujeme kliknutí sami
    }
}
```

---

## 6. Ochrana před Duplicitními Účty (Alt Abuse)

> [!NOTE]
> Toto je plánováno v **Fázi 5**.

Do té doby doporučujeme:

### Geyser + Floodgate Anti-Alt
```yaml
# Bedrock hráči jsou automaticky detekováni a odděleni
```

### LibertyBans / LiteBans IP Ban
```bash
# Zabanuj IP adresu duplicitního hráče
/ipban <player> Duplicitní účet
```

### Monitoring pomocí CoreProtect
```bash
# Sleduj aktivitu podezřelých hráčů
/co inspect
/co lookup u:<player> a:all t:1h
```

---

## 7. SQLite Thread-Safety

Plugin používá dedikovaný thread pro databázi:

```java
private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "BasicLandTuristika-DB");
    t.setDaemon(true);
    return t;
});
```

**Proč je to důležité:**
- SQLite neumí paralelní zápisy → bez ochrany → korupce dat
- `newSingleThreadExecutor()` zajišťuje, že v jednu chvíli píše vždy max 1 vlákno
- `WAL mode` navíc umožňuje souběžné čtení při zápisu

---

## 8. Doporučený Security Stack

Pro produkční BasicLand server s turistickým eventem doporučujeme:

```
✅ Paper 1.20+ (vestavěný Anti-Xray, PDC, TextDisplay)
✅ WorldGuard (ochrana lokací)
✅ Grim AntiCheat (pohybový anti-cheat - free)
✅ LibertyBans (správa banů + IP bany)
✅ CoreProtect (logging bloků + hráčů)
✅ ViaVersion (více klientských verzí)
✅ Spark (profiler - sleduj lag spikes)
```

```
❌ ClearLag (nezná PDC) → nahradit entity limity v Paper
❌ Starý PluginManager reload → použij /turista reload místo /reload
```
