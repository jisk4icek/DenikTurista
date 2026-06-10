# 🛡️ Anti-ClearLag & Anti-Cheat

Tato strana popisuje bezpečnostní opatření pluginu a doporučení pro zabezpečení eventu.

---

## 1. Ochrana airdropů před ClearLagem

### Jak to funguje

Každý airdrop item je při spawnu označen pomocí **PersistentDataContainer (PDC)**:

```java
PersistentDataContainer pdc = meta.getPersistentDataContainer();
pdc.set(new NamespacedKey("turistika", "airdrop_id"), PersistentDataType.STRING, stampId);
pdc.set(new NamespacedKey("turistika", "airdrop_slots"), PersistentDataType.INTEGER, maxPickups);
```

Tento tag přežívá:
- ✅ Restart serveru
- ✅ Chunk unload/reload
- ✅ Serializaci do NBT (item zůstane označen i v databázi)

### ClearLag Whitelist

Pro většinu ClearLag pluginů je potřeba nastavit whitelist. Pokud váš ClearLag používá whitelist přes custom metadata/NBT, přidejte klíč `turistika:airdrop_id`.

**Doporučené pluginy místo ClearLag:**
- [Chunky](https://modrinth.com/plugin/chunky) – pre-generace chunkú, snižuje lag
- Paper's vlastní entity cap – nastavení v `paper-world-defaults.yml`

---

## 2. Anti-Xray – Doporučení pro budoucí fáze

> [!NOTE]
> Přímá Anti-Xray implementace je plánována v **Fázi 5**.

Než bude Fáze 5 hotová, doporučujeme použít Paper's vestavěný Anti-Xray:

### Nastavení `paper-world-defaults.yml`

```yaml
anticheat:
  anti-xray:
    enabled: true
    engine-mode: 2    # Режим 2 je nejsilnější (fake bloky)
    max-block-height: 64
    update-radius: 2
    lava-obscures: false
    use-permission: false
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
      - mossy_cobblestone   # ← sem přidat bloky, za nimiž schováváme clue ke známkám!
      - stone
      - deepslate
    replacement-blocks:
      - stone
      - oak_planks
      - deepslate
```

### Skrývání "hint bloků" ke známkám

Pokud plánuješ v budoucnu umísťovat fyzické bloky jako nápovědy ke skrytým známkám, přidej jejich typy do `hidden-blocks`. Hráči s X-Ray texturama je neuvidí.

---

## 3. Anti-Cheat – Ochrana Zvedání Airdropů

Plugin implementuje vlastní ochranu v `EntityPickupItemEvent`:

```java
// Vždy zrušíme vanilla pickup
event.setCancelled(true);

// Pak provedeme vlastní logiku (DB check, slot kontrola, etc.)
```

Díky tomu:
- ❌ Hráč nemůže sebrat airdrop přímo do inventáře (obchází item entity)
- ✅ My zcela kontrolujeme, co se při pickup stane
- ✅ Databáze se zapisuje atomicky v jednovláknovém executoru

---

## 4. Ochrana GUI před Manipulací

Plugin zachytává **InventoryClickEvent** i **InventoryDragEvent** pro všechna naše GUI:

```java
@EventHandler
public void onInventoryDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder() instanceof Gui) {
        event.setCancelled(true);  // Blokuje drag do GUI
    }
}
```

Tím je znemožněno:
- Vložení vlastního itemu do deníkového GUI tažením
- Krádež předmětů z GUI výměnným trikem (shift-click exploity)

---

## 5. Thread-Safety SQLite

> [!IMPORTANT]
> SQLite nepodporuje paralelní zápisy. Bez ochrany by mohlo dojít ke korupci databáze.

Plugin řeší toto pomocí dedikovaného jednovláknového executoru:

```java
private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "BasicLandTuristika-DB");
    t.setDaemon(true);
    return t;
});
```

Každé `CompletableFuture` databázové operace explicitně specifikuje `dbExecutor`:
```java
CompletableFuture.supplyAsync(() -> { /* SQL operace */ }, dbExecutor);
```

Navíc je zapnut **WAL (Write-Ahead Logging)** režim SQLite pro lepší výkon při souběžném čtení:
```java
statement.execute("PRAGMA journal_mode=WAL;");
```

---

## 6. Doporučené Security Pluginy

Pro produkční server doporučujeme přidat:

| Plugin | Účel |
|---|---|
| **Matrix / Grim AntiCheat** | Detekce speed hack, fly hack |
| **CoreProtect** | Log všech bloků – dohledatelnost griefingu |
| **LibertyBans / LiteBans** | Správa banů |
| **ViaVersion** | Podpora starších klientů bez ztráty Paper funkcí |
