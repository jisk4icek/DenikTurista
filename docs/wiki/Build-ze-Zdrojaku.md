# 🔨 Sestavení ze Zdrojového Kódu

Tato strana je určena pro vývojáře, kteří chtějí plugin kompilovat sami nebo přispívat do projektu.

---

## Požadavky

| Nástroj | Minimální verze | Stažení |
|---|---|---|
| **Java JDK** | 17 | [adoptium.net](https://adoptium.net/) |
| **Maven** | 3.8+ | [maven.apache.org](https://maven.apache.org/) |
| **Git** | 2.x | [git-scm.com](https://git-scm.com/) |

---

## Krok za krokem

### 1. Klonování repozitáře

```bash
git clone https://github.com/jisk4icek/DenikTurista.git
cd DenikTurista
```

### 2. Sestavení

```bash
mvn clean package
```

Maven automaticky stáhne Paper API ze `repo.papermc.io`. Výsledný JAR bude v:
```
target/BasicLandTuristika-1.0.0.jar
```

### 3. Nasazení na testovací server

```bash
# Zkopíruj JAR do plugins složky testovacího serveru:
cp target/BasicLandTuristika-1.0.0.jar /cesta/k/testovaci-server/plugins/

# Restartuj server nebo použij PluginManager reload
```

---

## Struktura projektu

```
DenikTurista/
├── src/
│   └── main/
│       ├── java/cz/basicland/turistika/
│       │   ├── BasicLandTuristika.java    # Vstupní bod
│       │   ├── command/                   # Obsluha příkazů
│       │   ├── config/                    # Config a Message managery
│       │   ├── database/                  # SQLite, JDBC
│       │   ├── gui/                       # GUI API
│       │   ├── mechanics/                 # Herní mechaniky
│       │   └── utils/                     # Pomocné třídy
│       └── resources/
│           ├── plugin.yml
│           ├── config.yml
│           └── messages.yml
├── docs/
│   └── wiki/                              # Wiki stránky
├── pom.xml
├── README.md
└── LICENSE
```

---

## Závislosti (pom.xml)

Plugin má **nulové runtime závislosti**. Jediná závislost je Paper API (scope: `provided`):

```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.20.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

---

## Coding Guidelines

- Všechny DB operace **musí** běžet přes `dbExecutor`, nikdy přes `CompletableFuture.runAsync()` bez executoru
- Bukkit/Paper API (entity spawny, zvuky, zprávy hráčům) **musí** vždy běžet na hlavním vláknu přes `Bukkit.getScheduler().runTask()`
- Formátování datumů – **vždy** `DateTimeFormatter`, nikdy `SimpleDateFormat`
- Chat zprávy adminom – **používej** `ChatUtil` třídu pro konzistentní vizuál
