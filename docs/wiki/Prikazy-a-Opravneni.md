# 📋 Příkazy a Oprávnění

## Přehled příkazů

### `/denik`
Otevře GUI Turistického deníku pro hráče.

| Vlastnost | Hodnota |
|---|---|
| Aliasy | `/turistickydenik`, `/td` |
| Oprávnění | Žádné (dostupné všem hráčům) |
| Použití | `/denik` |

---

### `/turista`
Hlavní administrátorský příkaz.

| Vlastnost | Hodnota |
|---|---|
| Oprávnění | `turista.admin` (default: OP) |

#### Sub-příkazy

| Příkaz | Popis | Příklad |
|---|---|---|
| `/turista give <hráč> <id>` | Udělí hráči turistickou známku | `/turista give Steve hrad_karlstejn` |
| `/turista top` | Zobrazí TOP 10 hráčů v chatu | `/turista top` |
| `/turista list` | Vypíše všechny známky z configu | `/turista list` |
| `/turista info <hráč>` | Zobrazí progres konkrétního hráče | `/turista info Steve` |
| `/turista hologram spawn` | Spawnuje TextDisplay hologram na pozici admina | `/turista hologram spawn` |
| `/turista hologram remove` | Odstraní hlavní leaderboard hologram | `/turista hologram remove` |
| `/turista reload` | Znovunačte `config.yml` a `messages.yml` | `/turista reload` |

---

## Tab-doplňování

Plugin implementuje **plnohodnotný TabCompleter**:

```
/turista <TAB>          → give, top, list, info, hologram, reload
/turista give <TAB>     → seznam online hráčů
/turista give Steve <TAB> → seznam ID všech známek z configu
/turista info <TAB>     → seznam online hráčů
/turista hologram <TAB> → spawn, remove
```

---

## Oprávnění (Permissions)

| Permission node | Popis | Default |
|---|---|---|
| `turista.admin` | Přístup ke všem `/turista` sub-příkazům | OP |

### Nastavení oprávnění přes LuckPerms
```bash
# Udělit skupině "admin" práva:
/lp group admin permission set turista.admin true

# Odebrat oprávnění:
/lp group admin permission unset turista.admin
```

> [!NOTE]
> Příkaz `/denik` nevyžaduje žádné speciální oprávnění – je dostupný všem hráčům.
