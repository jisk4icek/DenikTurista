# 🪂 Systém Airdropů

Airdropy jsou **limitované edice turistických známek**, které v definovaný čas fyzicky spadnou na specifické souřadnice ve světě.

---

## Jak to funguje

```
1. V config.yml nastavíš spawn_time airdropu
2. AirdropManager každou minutu kontroluje, zda nastal čas spawnu
3. Fyzický item se spawnuje na zadaných souřadnicích s particles efektem
4. Do chatu jde broadcast s lokací airdrops
5. Hráči mají lifetime_minutes minut na sebrání
6. Posledních max_pickups hráčů dostane znamku do deníku
7. Po uplynutí času item zmizí s majestátním zvukovým efektem
```

---

## Konfigurace airdrops

```yaml
# config.yml
airdrops:
  muj_letni_drop:
    stamp_id: "letni_special"      # Musí existovat v sekci stamps!
    spawn_time: "2026-07-20 18:00:00"
    world: "world"
    x: 150.0
    y: 80.0
    z: -200.0
    max_pickups: 5                 # 5 nejrychlejších hráčů získá známku
    lifetime_minutes: 60           # Zmizí po 60 minutách, pokud ho nikdo nesebere
```

---

## Ochrana před ClearLagem

> [!IMPORTANT]
> Toto je **klíčová funkce**. Bez správného nastavení by ClearLag mohl airdrop předmět smazat!

Plugin označuje každý airdrop item pomocí **PersistentDataContainer (PDC)** tagem:
- **Namespace:** `turistika`
- **Key:** `airdrop_id`
- **Typ:** `STRING` (hodnota = ID knowńky)

### Konfigurace ClearLag pro whitelisting

Pokud používáš **ClearLag**, přidej do jeho `config.yml`:

```yaml
# ClearLag config.yml
entity-clear:
  enabled: true
  # Seznam entit, které NEBUDE ClearLag mazat:
  worlds:
    '*':
      enabled: true
      # Pokud ClearLag podporuje NBT/PDC whitelist:
      # Toto závisí na verzi ClearLag - viz jejich dokumentaci
```

> [!TIP]
> Modernější alternativy jako **EntityCleaner** nebo **RedstoneTools** mají přímou podporu PDC whitelistingu. Doporučujeme je preferovat před starším ClearLagem.

### Alternativní přístup – vlastní ClearLag plugin

Pokud váš ClearLag neumí whitelist přes PDC, může admin použít listener, který zabrání mazání označených itemů:

```java
@EventHandler
public void onClearEvent(EntityRemoveFromWorldEvent event) {
    if (event.getEntity() instanceof Item item) {
        ItemMeta meta = item.getItemStack().getItemMeta();
        if (meta != null && meta.getPersistentDataContainer()
                .has(AirdropManager.PDC_AIRDROP_ID, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}
```

---

## Particle efekty

Každý airdrop aktivně generuje particles kolem sebe každých 0,5 sekundy:
- `TOTEM_OF_UNDYING` – zlatý záblesk
- `END_ROD` – bílé částice

Při sebrání hráč obdrží:
- Sound: `ENTITY_PLAYER_LEVELUP`
- Particles: `TOTEM_OF_UNDYING` explodující z hráče

Při expiraci (nikdo nesebral):
- Sound: `ENTITY_LIGHTNING_BOLT_THUNDER`
- Particles: `EXPLOSION`

---

## Automatický broadcast

Při spawnu airdrops se do chatu automaticky odesílá zpráva:

```
[!] TURISTICKY AIRDROP - známka ✦ Letní Special ✦ spadla na 150, 80, -200 ve světě world!
```

Hráči tak okamžitě vědí, kde se airdrop nachází.
