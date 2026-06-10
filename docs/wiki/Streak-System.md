# 🔥 Streak Systém – Každodenní Motivace

Streak systém odměňuje hráče, kteří se každý den vrátí a nasbírají alespoň 1 novou turistickou zna mku.

---

## Jak To Funguje

```
Hráč získá zna mku → StreakManager.recordActivity(player)
         │
         ├── Byl aktivní VČERA?
         │    └── ANO  → streak += 1, odměna dle streak_rewards
         │
         ├── Byl aktivní DNES (ale dříve)?
         │    └── ANO  → nic, streak se nemění
         │
         └── Byl naposledy aktivní před >1 dnem?
              └── ANO  → streak se resetuje na 1
                        → Pokud byl streak ≥ 3, hráč dostane zprávu o přerušení
```

Streak data se ukládají do SQLite tabulky `player_streaks`:
- `uuid` – hráč
- `last_date` – datum poslední aktivity (`yyyy-MM-dd`)
- `streak` – aktuální délka série (počet dní)

---

## Konfigurace v config.yml

```yaml
streak_rewards:
  # Klíč = počet dní v řadě
  3:
    commands:
      - "give %player% experience_bottle 5"
      - "broadcast &e%player% &7má &63 dny &7turistického streaku! 🔥"
  7:
    commands:
      - "give %player% diamond 2"
      - "lp user %player% meta setprefix 100 [&5Výzkumník&r]"
      - "title %player% &57 Dní Streak!|&7Jsi opravdový průzkumník!"
  14:
    commands:
      - "give %player% netherite_ingot 1"
      - "broadcast &d%player% &7má ohromujících &d14 dní &7turistického streaku!"
  30:
    commands:
      - "give %player% netherite_block 1"
      - "lp user %player% parent add legendatourista"
      - "eco give %player% 25000"
      - "broadcast &4&l[30 DNÍ STREAK] &d%player% &7je absolutní turistická legenda!"
```

---

## Zprávy pro Hráče

V `messages.yml` jsou dvě klíčové zprávy:

```yaml
# Zpráva po každé aktivitě se streakm
streak_update: "&b⚡ &7Turistický streak: &e{streak} &7dní v řadě! Tak dál!"

# Zpráva při přerušení streaku (pokud byl streak ≥ 3)
streak_broken: "&c⚡ Tvůj streak &e{streak} &cdní byl přerušen! Začínáš od 1."
```

---

## Tipy pro Streak Design

| Délka streaku | Doporučená odměna |
|---|---|
| **3 dny** | Malá odměna (XP flašky, chat zpráva) |
| **7 dní** | Střední odměna (diamanty, prefix) |
| **14 dní** | Velká odměna (netherite, crate klíč) |
| **30 dní** | Epická odměna (rank, velké peníze, broadcast) |
| **100 dní** | Legendární odměna (vlastní titul, speciální zna mka) |

> [!TIP]
> Streak systém je nejúčinnější, když jsou zna mky rozmístěny tak, že hráč **musí každý den někam jít** – ne jen jednou vychytat všechny.

---

## Výhody Streak Systému pro Server

- 📈 **Zvyšuje denní aktivitu** – hráči se přihlásí i když nemají čas na dlouhé hraní
- 🔄 **Retence** – hráči se bojí přerušit streak → vrací se
- 📊 **Měřitelný engagement** – admini vidí kdo je aktivní každý den
- 🎮 **Gamifikace** – přidává denní "quest" bez nutnosti quest pluginu
