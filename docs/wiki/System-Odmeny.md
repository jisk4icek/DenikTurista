# 🎁 Systém Odměn – Co Plugin Umí Dát a Jak

Tato strana popisuje **všechny typy odměn**, které plugin může automaticky udělovat, a **jak je konfigurovat** v `config.yml`.

---

## Jak Odměny Fungují

Plugin spouští odměny jako **konzolové příkazy** – to znamená, že se spouštějí s právy konzole (root), nikoliv hráče. Hráč proto nepotřebuje žádná extra oprávnění.

```
Hráč získá N-tou známku
         │
MilestoneManager zjistí, zda config obsahuje sekci milestones.N
         │
RewardManager.executeRewards(player, commands)
         │
         ├── "broadcast ..."    → Bukkit.broadcastMessage()
         ├── "title ..."        → player.sendTitle() (nativní API)
         └── Cokoli jiného      → Bukkit.dispatchCommand(CONSOLE, cmd)
```

Placeholder `%player%` se automaticky nahradí jménem hráče.

---

## Trigger Body – Kdy Se Odměny Spouštějí

| Trigger | Kde v config.yml | Popis |
|---|---|---|
| **N-tá známka** | `milestones.<N>.commands` | Hráč celkem nasbírá N známek |
| **N dní v řadě** | `streak_rewards.<N>.commands` | Streak – každý den alespoň 1 známka |
| **1. na serveru** | `server_first_rewards.1.commands` | První hráč s kompletním deníkem |
| **2. na serveru** | `server_first_rewards.2.commands` | Druhý hráč s kompletním deníkem |
| **3. na serveru** | `server_first_rewards.3.commands` | Třetí hráč s kompletním deníkem |
| **Ruční udělení** | – | Admin zadá `/turista give <hráč> <id>` |

---

## Typy Odměn

### 1. 📦 Předměty (Items)
Použij vanilla `/give` příkaz:
```yaml
commands:
  - "give %player% diamond 5"
  - "give %player% diamond_sword 1"
  - "give %player% written_book 1"
```

### 2. 💰 Virtuální Měna – EssentialsX Economy
```yaml
commands:
  - "eco give %player% 1000"          # EssentialsX
  - "balance give %player% 1000"       # CMI Economy
  - "deposit %player% 1000"            # jiný vault plugin
```

### 3. 🏅 Ranky a Skupiny – LuckPerms
```yaml
commands:
  # Přidej hráče do skupiny (rank up)
  - "lp user %player% parent add vip"
  - "lp user %player% parent add turistamaster"

  # Nastav prefix
  - "lp user %player% meta setprefix 100 [&6Turista&r]"

  # Nastav suffix
  - "lp user %player% meta setsuffix 100 &7[Průzkumník]"
```

### 4. 📢 Chat Broadcast
```yaml
commands:
  # Standardní broadcast (zelená zpráva)
  - "broadcast &e%player% &7nasbíral 10 turistických známek!"

  # Broadcast se stylem (plugin NATIVNĚ zpracuje "broadcast" klíčové slovo)
  - "broadcast &d&l★ TURISTA ★ &a%player% &ajako první dokončil deník!"
```

### 5. 🎬 Title / Subtitle (Velký nápis na obrazovce)
Plugin má vlastní zkrácený formát – nemusíš psát JSON:
```yaml
commands:
  # Formát: title <nadpis>|<podnadpis>
  - "title %player% &6Gratulace!|&7Dosáhl jsi 10 známek"
  - "title %player% &d&lGRAND FINALE!|&aDokončil jsi jako PRVNÍ na serveru!"
```
> Text před `|` = nadpis, text za `|` = podnadpis. Obě části podporují `&` barvy.

### 6. 🔑 Crate Klíče (SuprisedCrates, CrazyCrates, ExcellentCrates)
```yaml
commands:
  # ExcellentCrates
  - "crates key give %player% turisticky 1"

  # CrazyCrates
  - "crazycrates givevirtualkey %player% TuristickyBednicek 1"

  # SuprisedCrates
  - "sc key give turistika %player% 1"
```

### 7. ⚔️ McMMO XP
```yaml
commands:
  - "mcmmo exp %player% mining 1000"
  - "mcmmo exp %player% herbalism 500"
```

### 8. 🌟 Zkušenosti / Levely
```yaml
commands:
  - "xp add %player% 500 levels"     # Bukkit nativní
  - "xp add %player% 10000 points"
```

### 9. 🎭 Custom Efekty (Sound, Particle přes příkaz)
Particle a sound efekty při získání známky jsou zabudované v pluginu. Pro extra efekty přes příkazy:
```yaml
commands:
  - "particle minecraft:totem_of_undying ~ ~1 ~ 0.5 0.5 0.5 0.1 50 force %player%"
```

### 10. 🤖 Discord Integrace (DiscordSRV / GatorBot)
```yaml
commands:
  # DiscordSRV – pošle zprávu do Discord kanálu
  - "discordsrv discord-broadcast turistika-event &aHráč **%player%** dokončil deník!"

  # GatorBot – přidá Discord roli
  - "gatorbot addrole %player% TuristaMaster"
```

---

## Příklad Kompletní Milestone Sekce

```yaml
milestones:
  1:
    commands:
      - "broadcast &7[Turistika] &e%player% &7získal svou první turistickou známku! 🗺️"
  5:
    commands:
      - "give %player% diamond 3"
      - "title %player% &e⭐ 5 známek! ⭐|&7Průzkumník se probouzí..."
      - "broadcast &7[Turistika] &e%player% &7nasbíral již &65 &7známek!"
  10:
    commands:
      - "give %player% diamond 10"
      - "lp user %player% meta setprefix 100 [&6Turista&r]"
      - "eco give %player% 2000"
      - "title %player% &6✦ Zlatý Turista ✦|&710 známek – Zlatý milník!"
      - "broadcast &6[Turistika] &6%player% &7dosáhl &6ZLATÉHO milníku – &e10 známek!"
  20:
    commands:
      - "give %player% netherite_ingot 1"
      - "lp user %player% parent add tourmaster"
      - "eco give %player% 10000"
      - "crates key give %player% touristicky 3"
      - "broadcast &d&l[★ TURISTIKA ★] &d%player% &7dosáhl bájného &d20 známek!"
      - "title %player% &d&l⚡ LEGENDA ⚡|&d20 známek – Jsi legenda BasicLandu!"
```

---

## Streak Odměny – Po Sobě Jdoucí Dny

Streak motivuje hráče, aby se přihlašovali každý den a sbírali alespoň 1 známku.

```yaml
streak_rewards:
  3:
    commands:
      - "give %player% experience_bottle 5"
      - "broadcast &e%player% &7má &63 dny &7turistického streaku! 🔥"
  7:
    commands:
      - "give %player% diamond 2"
      - "lp user %player% meta setprefix 100 [&5Výzkumník&r]"
      - "title %player% &57 Dní Streak!|&7Jsi opravdový průzkumník!"
  30:
    commands:
      - "give %player% netherite_block 1"
      - "lp user %player% parent add legendatourista"
      - "eco give %player% 25000"
      - "broadcast &d&l[STREAK] &d%player% &7má neuvěřitelných &d30 dní &7streaku!"
```

---

## Doporučení – Co Hráče Nejvíce Motivuje

Na základě zkušeností s herními eventy:

| Typ odměny | Motivační síla | Poznámka |
|---|---|---|
| **Server-First broadcast** | ⭐⭐⭐⭐⭐ | Veřejná sláva – nejúčinnější |
| **Exkluzivní rank/prefix** | ⭐⭐⭐⭐⭐ | Viditelný v chatu = prestižní |
| **Velká finanční odměna** | ⭐⭐⭐⭐ | Závisí na ekonomice serveru |
| **Title na obrazovce** | ⭐⭐⭐⭐ | Okamžitá gratifikace |
| **Crate klíč** | ⭐⭐⭐⭐ | Přidává prvek náhody a vzrušení |
| **Streak systém** | ⭐⭐⭐⭐ | Denní motivace se vracet |
| **Diamanty / items** | ⭐⭐⭐ | Klasika, ale méně výjimečné |

> [!TIP]
> Kombinuj **viditelné** odměny (broadcast, prefix) s **materiálními** (itemy, peníze). Hráče motivuje jak veřejné uznání tak konkrétní zisk.
