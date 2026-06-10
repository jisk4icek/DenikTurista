# ❓ FAQ – Nejčastější dotazy

---

## Instalace a Kompatibilita

**Q: Funguje plugin na Spigotu?**  
A: **Ne.** Plugin využívá Paper-specifické API – konkrétně `TextDisplay` entity, `PersistentDataContainer` (PDC) na itemech a `Billboard` transformace. Bez Paper 1.20+ tyto třídy neexistují.

---

**Q: Funguje plugin na verzi 1.19 nebo starší?**  
A: **Ne.** `TextDisplay` entity byly přidány v Minecraft 1.19.4. Hologramový systém by nefungoval. Minimální verze je **Paper 1.20**.

---

**Q: Mohu plugin použít s Geyser (Bedrock)?**  
A: Částečně. GUI inventáře a zprávy budou fungovat, ale TextDisplay hologramy nemusí být správně viditelné pro Bedrock klienty. Testování s Floodgate + Geyser je doporučeno.

---

## Konfigurace

**Q: Jak přidám novou turistickou známku?**  
A: Otevři `config.yml`, přidej nový blok pod `stamps:` a zavolej `/turista reload`. Viz [⚙️ Konfigurace](Konfigurace).

---

**Q: Mohu mít neomezený počet známek?**  
A: Ano. GUI automaticky stránkuje – každá strana pojme 45 položek.

---

**Q: Jak nastavím časový zámek na knowńku?**  
A: Přidej klíč `unlock_date` do konfigurace knowńky:
```yaml
unlock_date: "2026-07-01 12:00:00"
```
Formát musí být přesně `yyyy-MM-dd HH:mm:ss`. Čas je serverový čas.

---

**Q: Airdrop se nespawnuje v nastavený čas. Co je špatně?**  
A: Zkontroluj:
1. Správný formát `spawn_time` (musí být `yyyy-MM-dd HH:mm:ss`)
2. `stamp_id` musí existovat v sekci `stamps`
3. `world` musí být správný název světa (ne "World" s velkým W)
4. Airdrop se spawnuje, pouze pokud server běžel v okamžiku spawn_time ± 65 minut

---

## Databáze

**Q: Kde je uložená databáze?**  
A: `plugins/BasicLandTuristika/database.db` – SQLite soubor. Zálohuj ho pravidelně!

---

**Q: Mohu přenést data na jiný server?**  
A: Ano, zkopíruj soubor `database.db` do složky `plugins/BasicLandTuristika/` na novém serveru.

---

**Q: Jak resetovat data hráče?**  
A: Přímo přes SQLite CLI nebo DB Browser pro SQLite:
```sql
DELETE FROM unlocked_stamps WHERE uuid = 'uuid-hrace-zde';
```

---

## Chyby

**Q: V konzoli vidím `Nelze se pripojit k SQLite databazi!`**  
A: Plugin nemohl vytvořit nebo otevřít soubor `database.db`. Zkontroluj oprávnění ke složce `plugins/BasicLandTuristika/`.

---

**Q: Hologram se po restartu neobnoví.**  
A: Zkontroluj, zda svět, kde je hologram umístěn, se načetl dříve než plugin spouštěl inicializaci. Plugin čeká 3 sekundy (60 ticků) po startu. Pokud se svět načítá déle, zvyš zpoždění v `BasicLandTuristika.java`.

---

**Q: Hráč nemůže otevřít `/denik`.**  
A: Příkaz `/denik` nevyžaduje žádná oprávnění. Pokud nefunguje, zkontroluj, zda je plugin správně načten (`/plugins` seznam).

---

**Q: ClearLag maže moje airdropy!**  
A: Viz [🛡️ Anti-ClearLag & Anti-Cheat](Anti-ClearLag-a-Anti-Cheat) pro podrobné instrukce.
