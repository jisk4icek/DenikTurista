# 🪪 NPC Systém – Turistické Postavy (v2.0)

Turistické NPC jsou **Villager postavy** spawnuté pluginem, které rozdávají zna mky při kliknutí. Žádný plugin třetí strany není potřeba – vše funguje nativně.

---

## Jak NPC Fungují

```
Admin zadá: /turista npc spawn hrad_karlstejn
         │
         └── Spawne Villager entitu na admině pozici
               │
               ├── Entita je označena PDC tagem (turistika:npc_stamp_id)
               ├── Má jméno: "✦ {název zna mky} ✦ [Klikni pro získání]"
               ├── AI = false (nestěhuje se)
               ├── Invulnerable = true (nelze zabít)
               └── UUID + pozice uloženy v SQLite

Hráč klikne pravým tlačítkem na NPC
         │
         ├── Plugin zkontroluje PDC tag
         ├── Zkontroluje dostupnost zna mky (časové okno, expiraci)
         ├── Zkontroluje zda hráč nemá zna mku již
         └── Udělí zna mku + particles + zvuk + streak + milestone
```

---

## Příkazy

| Příkaz | Popis |
|---|---|
| `/turista npc spawn <stamp_id>` | Spawne NPC na tvé aktuální pozici |
| `/turista npc remove <stamp_id>` | Odstraní NPC (entita + záznam v DB) |
| `/turista npc list` | Vypíše všechna aktivní NPC s UUID |

---

## Konfigurace NPC

NPC **není potřeba nastavovat v config.yml** – vše se spravuje in-game příkazy. Pozice se ukládají do SQLite databáze a po restartu se NPC automaticky respawnují.

---

## NPC vs. Fyzická Lokace

Oba systémy lze kombinovat na té samé zna mce:

| | Fyzická Lokace (`setlocation`) | NPC (`npc spawn`) |
|---|---|---|
| **Získání** | Hráč vstoupí do okruhu | Hráč klikne na postavu |
| **Viditelnost** | Neviditelné (hinted ActionBarem) | Viditelná postava s jménem |
| **Radius** | Nastavitelný (metrový okruh) | Přesný klik |
| **Vhodné pro** | Přírodní lokace, krajinu | Vesnice, budovy, NPC obchody |

> [!TIP]
> Pro zajímavý event je ideální **kombinace obou** – fyzická lokace v přírodě, NPC pro reward stanici ve vesnici.

---

## Restart Serveru

- NPC data jsou uložena v `plugins/BasicLandTuristika/database.db`
- Po restartu plugin automaticky načte NPC z DB a respawnuje entity
- Pokud entita stále existuje (Persistent=true), plugin ji znovu použije bez respawnu

---

## Omezení

- NPC jsou **Villager** entity – nelze změnit skin bez Citizens pluginu
- Jedna zna mka = jedno NPC (nelze mít více NPC pro tu samou zna mku)
- NPC nedetekuje Residence ochranu – kliknutí funguje všude
