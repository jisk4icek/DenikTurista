# ⚡ Instalace

## Požadavky

| Požadavek | Minimální verze |
|---|---|
| **PaperMC** | 1.20.1+ |
| **Java** | 17+ |
| **RAM** | Bez požadavku (SQLite, bez cache) |

> [!WARNING]
> Plugin **nefunguje na Spigotu** – využívá Paper-specifické API (TextDisplay entity, PDC na itemech).

---

## Krok za krokem

### 1. Stáhni JAR
Nejnovější verzi pluginu stáhni ze záložky [Releases](../../releases).

### 2. Vlož do složky plugins
```
server/
└── plugins/
    └── BasicLandTuristika-1.0.0.jar  ← sem
```

### 3. Restartuj server
Spusť nebo restartuj svůj Paper server. Plugin se automaticky inicializuje a vytvoří složku s konfigurací:

```
plugins/
└── BasicLandTuristika/
    ├── config.yml      ← hlavní konfigurace
    ├── messages.yml    ← všechny texty a zprávy
    └── database.db     ← SQLite databáze (auto-generováno)
```

### 4. Uprav konfigurace
Otevři `config.yml` a přidej svoje turistické známky. Viz [⚙️ Konfigurace](Konfigurace).

### 5. Reload (bez restartu)
Jakmile konfiguraci upravíš, použij:
```
/turista reload
```

---

## Ověření instalace

Po startu serveru by se v konzoli měl objevit banner:

```
[BasicLandTuristika]   ██████╗ ███████╗███╗  ██╗██╗██╗  ██╗
[BasicLandTuristika]   ...
[BasicLandTuristika]   BasicLand Turistika  v1.0.0
[BasicLandTuristika]   Paper 1.20+  |  SQLite  |  Zero Dependencies
[BasicLandTuristika] Plugin uspesne nacten a pripraven!
```

Pokud banner chybí nebo vidíš chybu, viz [❓ FAQ](FAQ).
