# 📍 Nastavení Fyzických Lokací Znamek

Lokace jsou místa ve světě, kde hráč musí fyzicky navštívit, aby získal turistickou zna mku.

---

## Jak to funguje

Plugin každé **2 sekundy** kontroluje, zda se nějaký online hráč nenachází v nastaveném okruhu (radius) kolem lokace zna mky. Pokud ano a hráč zna mku ještě nemá, automaticky ji dostane.

Navíc, pokud je hráč v **3× radius** (blízkosti), zobrazí se mu v ActionBaru hint se vzdáleností.

---

## Metoda 1 – Příkaz (Doporučeno)

Nejjednodušší způsob. Přejdi fyzicky na dané místo ve hře a zadej příkaz:

```
/turista setlocation <id_znamky> [radius]
```

**Příklady:**
```bash
# Nastav lokaci s výchozím radiusem 5m
/turista setlocation hrad_karlstejn

# Nastav lokaci s vlastním radiusem 10m
/turista setlocation letni_festival_2026 10.0

# Nastav malý bod (např. konkrétní blok) s radiusem 2m
/turista setlocation tajny_vchod 2.0
```

Plugin automaticky zapíše souřadnice do `config.yml` pod klíč `stamps.<id>.location`.

---

## Metoda 2 – Ruční Editace config.yml

Otevři `plugins/BasicLandTuristika/config.yml` a přidej sekci `location` ke zna mce:

```yaml
stamps:
  hrad_karlstejn:
    name: "&6✦ Hrad Karlštejn ✦"
    lore:
      - "&7Monumentální hrad uprostřed serveru."
    material: PAPER
    custom_model_data: 1001
    # ↓ Fyzická lokace v herním světě
    location:
      world: "world"        # Název světa (musí existovat!)
      x: 152.5              # Přesná X souřadnice
      y: 68.0               # Y souřadnice (ignoruje se při 2D výpočtu)
      z: -304.0             # Přesná Z souřadnice
      radius: 8.0           # Poloměr v metrech (bloky)
```

Po editaci zavolej `/turista reload`.

---

## Metoda 3 – Zna mka bez Lokace (Admin-only nebo Airdrop)

Pokud zna mka nemá nastavenou sekci `location`, hráč ji nemůže najít procházením světa. Dostane ji pouze:
- Přes příkaz `/turista give`
- Sebrání airdrops
- Voláním příkazu z NPC, CommandBlocku, quest pluginu

Takové zna mky označí `/turista list` jako `[---]`.

---

## Správa Lokací

| Příkaz | Popis |
|---|---|
| `/turista setlocation <id> [r]` | Nastaví lokaci na tvé aktuální pozici |
| `/turista removelocation <id>` | Odstraní lokaci ze zna mky |
| `/turista locations` | Vypíše všechny nastavené lokace se souřadnicemi |
| `/turista list` | Přehled všech zna mek + zda mají lokaci |

---

## Jak Nastavit Ideální Radius

| Situace | Doporučený radius |
|---|---|
| Konkrétní blok/NPC | `2.0` – `3.0` |
| Menší budova (interiér) | `5.0` – `8.0` |
| Větší área (věž, brána) | `10.0` – `15.0` |
| Celý ostrov / čtvrť | `20.0` – `50.0` |
| Biom / velká oblast | `50.0` – `100.0` |

> [!TIP]
> Pro turistický event doporučujeme **malé radiusy (3-8m)** kolem konkrétních bodů – hráče to nutí skutečně dojít na přesné místo.

---

## Hint v ActionBaru

Když je hráč v **3× radius** (ale ne ještě uvnitř), zobrazí se mu:
```
✦ Jsi blízko turistické zna mky ✦ Hrad Karlštejn ✦ (12m) ✦
```

Hint se zobrazuje **maximálně jednou za 30 sekund** na každou zna mku, aby neobtěžoval.

Hráč tak cítí, že se blíží k cíli, aniž by ho plugin přesně naváděl šipkami – zachovává prvek průzkumu.

---

## Tip – Kombinace s Resource Packem

Pro prémiový zážitek:
1. Vytvoř vlastní 3D model turistické tabule (resource pack, `custom_model_data`)
2. Umísti v herním světě blok s tímto modelem (např. přes dekorace)
3. Nastav lokaci na souřadnice tohoto "sloupu"
4. Hráč fyzicky přijde k tabuli a zna mka se zaeviduje

---

## Rozšíření – NPC Integrace (Citizens)

Pokud máš na serveru plugin **Citizens**, nastav NPC command:

```
/npc command add -p /turista give <player> id_znamky
```

Tím hráč dostane zna mku kliknutím na NPC. Lokaci pak nemusíš nastavovat – NPC je "lokace" sám o sobě.
