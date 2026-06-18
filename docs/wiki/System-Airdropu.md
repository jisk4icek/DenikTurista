# 🪂 Airdropy v2.0 – Náhodný Spawn v Regionu

Airdropy v2.0 podporují **náhodný bezpečný spawn** v konfigurovatelné oblasti mapy, nejen pevné souřadnice.

---

## Dvě Metody Spawnu

### Metoda 1: Pevné Souřadnice (klasické)

```yaml
airdrops:
  airdrop_spawn_01:
    stamp_id: "podzimni_special"
    spawn_time: "2026-07-15 20:00:00"
    world: "world"
    x: 0.5
    y: 65.0
    z: 0.5
    max_pickups: 5
    lifetime_minutes: 60
```

### Metoda 2: Náhodný Region (v2.0)

```yaml
airdrops:
  airdrop_random_01:
    stamp_id: "podzimni_special"
    spawn_time: "2026-07-22 20:00:00"
    max_pickups: 3
    lifetime_minutes: 60
    spawn_region:
      world: "world"
      min_x: -500   # Levá hranice regionu
      max_x: 500    # Pravá hranice regionu
      min_z: -500   # Přední hranice
      max_z: 500    # Zadní hranice
```

Plugin automaticky:
1. Vybere náhodné X, Z v zadaných hranicích
2. Najde nejvyšší pevný blok (povrch) pomocí `world.getHighestBlockYAt()`
3. Vyhne se kapalným blokům (voda, láva)
4. Vyhne se Protected Zones
5. Pokud nenajde vhodné místo za 60 pokusů → loguje varování, airdrop se nespawní

---

## Protected Zones – Kde Airdropy Nespadnou

```yaml
protected_zones:
  spawn_area:
    world: "world"
    min_x: -100
    max_x: 100
    min_z: -100
    max_z: 100

  vesnice_trhove_namesti:
    world: "world"
    min_x: 500
    max_x: 550
    min_z: -200
    max_z: -150
```

Airdropy nespadnou do žádné definované Protected Zone. Totéž platí pro proximity systém – automatická známka se neudělí v Protected Zone.

---

## Broadcast s Souřadnicemi

Při spawnu airdropu plugin automaticky broadcastuje zprávu obsahující přesné souřadnice:

```
[!] TURISTICKÝ AIRDROP ✦ Podzimní Special » 234 68 -156 (svět: world)
 Použij: /tppos 234 68 -156 (nebo teleport na souřadnice)
```

Hráči mohou souřadnice zadat do EssentialsX `/tppos`, nebo je dát do mapy.

---

## Tipy pro Airdropy

| Nastavení | Doporučení |
|---|---|
| **Region velikost** | 500×500 bloků = velký event, 100×100 = malý |
| **max_pickups** | 1 = exkluzivní (jen jeden hráč), 10+ = pro všechny |
| **lifetime_minutes** | 30–120 min, kratší = více napětí |
| **spawn_time** | Večer (19:00-21:00) = nejvíce hráčů online |

> [!TIP]
> Kombinuj `max_pickups: 1` s velkým regionem pro ultimátní honbu za airdropem!

> [!WARNING]
> Pokud `spawn_region` je příliš malý nebo celý pokrytý Protected Zones, airdrop se nespawní. Zkontroluj konzoli serveru.
