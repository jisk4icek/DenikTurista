# 🔥 Jak Hráče Motivovat – Design Eventu

Tato strana poskytuje herní designové tipy, jak udělat turistický event co nejzábavnější.

---

## Psychologie Herních Eventů

Turistický event funguje nejlépe, když kombinuje **3 pilíře zábavy**:

```
┌──────────────────────────────────────────────────────┐
│                  3 PILÍŘE ZÁBAVY                      │
│                                                        │
│  1. PRŮZKUM        2. SOUTĚŽ          3. ODMĚNA       │
│  "Kde to je?"     "Kdo je první?"    "Co za to?"      │
│                                                        │
│  → Zvídavost      → Rivalita         → Satisfakce    │
└──────────────────────────────────────────────────────┘
```

---

## 1. Průzkum – Design Lokací

### Kde umístit turistické body?
- **Ikonická místa** na serveru (spawn, největší stavby, biomy)
- **Skrytá zákoutí** která většina hráčů nezná
- **Těžko dostupná místa** (vrchol hory, spodek oceánu, Nether pevnost)
- **Sezónní/eventová místa** (festival area, halloween dungeon)

### Gradace obtížnosti
```
Lehké (velký radius 10-20m)   → 50% známek → Pro všechny
Střední (radius 5-8m)         → 35% známek → Pro zkušené
Těžké (radius 2-3m)           → 15% známek → Pro hardcore
```

### Hint Systém (bez kódu, jen s designem)
Přidej na veřejný Discord kanál nebo /warp hints:
- Enigmatické popisky: *"Hledej tam, kde kamenné stráže hlídají vstupy..."*
- Souřadnice s offsetem: *"X: 150 ± 100, Z: -200 ± 100"*
- Fotky bez souřadnic

---

## 2. Soutěž – Kompetitivní Elementy

### Co Plugin Poskytuje Out-of-the-Box
| Mechanika | Popis |
|---|---|
| **TextDisplay Žebříček** | Živý leaderboard na spawnu |
| **Server-First Broadcast** | Každá první známka je ohlášena |
| **Grand Finale** | Dramatická odměna pro 1./2./3. s dokončením |
| **Streak Systém** | Denní race – kdo má nejdelší sérii |

### Jak Zvýšit Kompetitivitu
1. **Zveřejňuj průběžný stav** – `/turista top` sdílej na discord denně
2. **Přidej countdown** – "Do konce eventu zbývá 7 dní" v MOTD nebo tab
3. **Airdropy s časovým omezením** – vyvolaj paniku a rush
4. **Limitovaná edice** s `max_pickups: 1` – jen jeden hráč může získat!

---

## 3. Odměny – Co Hráče Drží

### Správná Pyramida Odměn

```
          🥇 1. MÍSTO (dokončení)
         /  Grand finale, exkluzivní rank, velké peníze
        /
       🎯 MILNÍKY (5, 10, 15, 20 známek)
      /  Postupné odměny, prefix, crate klíče
     /
    🔄 STREAK (každodenní login)
   /  Malé ale časté odměny, bonus XP
  /
 ⭐ PRVNÍ KROK (1. známka)
   Broadcast, title, malá odměna – první dojem je klíčový!
```

### Timing Odměn
- **Okamžitě po získání** – zvuk + particles + zpráva (zabudováno)
- **Milestone** – title + broadcast (okamžitě, automaticky)
- **Denně** – streak odměna (automaticky)
- **Po eventu** – manuální speciální odměny přes `/turista give`

---

## 4. Časové Zámky – Jak Protáhnout Event

Místo aby hráči sebrali vše za den, rozděluj odemykání:

```yaml
stamps:
  znamka_tydne_1:
    name: "&eZná mka 1. Týdne"
    unlock_date: "2026-07-01 12:00:00"   # Den 1

  znamka_tydne_2:
    name: "&eZná mka 2. Týdne"
    unlock_date: "2026-07-08 12:00:00"   # Den 8

  znamka_finale:
    name: "&dFinálová Zná mka"
    unlock_date: "2026-07-28 20:00:00"   # Den 28 – velké finále
```

Hráči se vrací každý týden pro novou zná mku → dlouhodobá retence.

---

## 5. Airdropy – Event v Eventu

Airdropy jsou nejúčinnější nástroj pro **real-time vzrušení**:

### Doporučená Strategie Airdropů
```
Pátek 20:00 – Airdrop na spawnu       (max_pickups: 10, velký radius)
Sobota 15:00 – Airdrop v lesích       (max_pickups: 5, hidden location)  
Neděle 18:00 – Grand Airdrop          (max_pickups: 1, ULTRA vzácná zná mka)
```

### Budování Napětí Před Airdropem
Ručně broadcastuj hodinu před airdropem:
```
/broadcast &b&l[AIRDROP ZA 1 HODINU] &7Připravte se! Vzácná turistická zná mka spadne na souřadnicích X:0 Z:0!
```

---

## 6. Sociální Mechaniky

### Co Plugin Automaticky Dělá
- Broadcast když někdo najde známku jako první na serveru
- Broadcast milníků (5, 10, 20 známek)
- Broadcast dokončení (Grand Finale)
- ActionBar hint při přiblížení k lokaci

### Co Přidat Ručně (Doporučení)
| Aktivita | Jak |
|---|---|
| Discord kanál #turistika-event | Zveřejňuj screenshoty, tipy, airdrop časy |
| Týdenní /top screenshot | Shareuj žebříček každý týden |
| Votování o přístiích airdrop lokaci | Zapojuje komunitu |
| Speciální event streamer | Pozvání youtuber/streamer pro hype |

---

## 7. Příkladový 30denní Event Plán

```
Týden 1 (1.-7.7.):  Odemknutí 30% známek, Airdrop každý pátek
Týden 2 (8.-14.7.): Odemknutí dalších 30%, Streak odměny startují
Týden 3 (15.-21.7.): Odemknutí dalších 30%, Grand Airdrop (max_pickups: 1)
Týden 4 (22.-28.7.): Finální známka, Race o Server-First dokončení
Den 28 (28.7.):     Slavnostní Grand Finale, vyhlášení výherců
```

---

## 8. Rozšiřující Nápady pro Budoucí Fáze

> [!TIP]
> Tyto funkce jsou plánovány nebo je lze implementovat rozšířením pluginu:

| Nápad | Popis | Fáze |
|---|---|---|
| **Hinty za body** | Hráč utratí herní měnu za souřadnicový hint | Fáze 5 |
| **Album Foto** | Hráč pořídí "screenshot" (book & quill) na lokaci | Fáze 5 |
| **Týmový mód** | Hráči sbírají známky do sdíleného týmového deníku | Budoucnost |
| **Sezónní Deníky** | Léto/Podzim/Zima/Jaro – každá sezóna jiné známky | Budoucnost |
| **PlaceholderAPI** | `%turistika_streak%` v TAB, scoreboard | Fáze 6 |
| **Boss Bar Odpočet** | Countdown do dalšího airdrops na obrazovce | Fáze 8 |
