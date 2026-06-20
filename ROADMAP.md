# TheSettler_x_Create – Cleanup & Improvement Roadmap

Erstellt nach Code-Analyse, Juni 2026.
Keine Änderungen wurden vorgenommen. Dies ist eine reine Analyse und Planung.

---

## State-Drift – Root-Cause-Analyse

Bevor die Roadmap, weil alles andere darauf aufbaut.

### Das eigentliche Problem

Es gibt **zwei unabhängige State-Stores**, die dasselbe Request-Lifecycle tracken aber keinen gemeinsamen stabilen Schlüssel haben:

**Store A – Resolver (In-Memory)**
`CreateShopRequestStateMachine` speichert `CreateShopFlowRecord` (FlowState pro Token) **nur im RAM**.
Nach einem World-Reload ist dieser Store leer. Der `RehydrateService` versucht ihn aus dem
MineColonies-Request-Graphen zu rekonstruieren – aber das ist eine Heuristik, keine echte
Wiederherstellung. Ein Request der in `DELIVERY_CREATED` war, wird nach dem Reload als
"mindestens 1 pending" rekonstruiert. Der Resolver sieht offene Arbeit und bestellt ggf. nochmal.

**Store B – Block Entity (NBT-persistent)**
`CreateShopBlockEntity` speichert `InflightEntry` in NBT. Das Persistieren selbst ist korrekt.
**Aber:** jeder Eintrag verwendet `requesterName` (String) + `address` (String) als Identität.
Diese Strings driften, wenn MineColonies einen Bürger umbenennt, ein Gebäude neu registriert
oder der Resolver neu zugewiesen wird.

**Die Verbindung fehlt:**
`FlowRecord` nutzt `IToken<?>` (intern eine UUID) als Key.
`InflightEntry` nutzt String-Keys.
Es gibt **keinen gemeinsamen stabilen Schlüssel** zwischen beiden. Deshalb können sie nach einem
Reload oder Token-Drift nicht mehr aufeinander abgeglichen werden.

**Symptome daraus:**
- Inflight-Einträge die nie consumed werden → endlose Lost-Package-Dialoge
- RehydrateService-Heuristik führt zu Doppel-Bestellungen
- Jede Session fügt weitere "fallback string matching"-Guards hinzu statt das Problem zu lösen

### Die Lösung

**Request-UUID als gemeinsamer Schlüssel.**

1. `InflightEntry` bekommt ein `requestUuid`-Feld (UUID aus `IToken<?>`).
   String-Felder (requesterName, address) bleiben, aber nur als Display-Metadata – nicht für
   Matching verwendet. Matching läuft über UUID first, String als Fallback für Migration.

2. `FlowState` wird in NBT persistiert (in `TileEntityCreateShop` oder `CreateShopBlockEntity`):
   `Map<UUID, CreateShopFlowState>` → NBT. Auf Reload: exakte Wiederherstellung, keine Heuristik.

3. `RehydrateService` wird einfacher: FlowState aus NBT laden, dann nur noch prüfen ob der
   MineColonies-Request noch existiert. Kein "ableiten aus Request-Graph" mehr nötig.

4. Beim Cancel/Complete: Inflight-Einträge direkt per UUID bereinigen. Keine zeitbasierte
   Überprüfung mit String-Matching mehr nötig.

**Migration für bestehende Saves:**
Inflight-Einträge ohne UUID behalten String-Matching als Fallback – best-effort wie bisher.
Sobald ein Request mit UUID gelöscht wird, werden seine String-Einträge aufgeräumt.

---

## Roadmap

Die Phasen sind nach Risiko geordnet. Jede Phase ist unabhängig deploybar.

---

### Phase 1 – Safe Cleanup (Niedriges Risiko)

Keine Logikänderungen. Nur Struktur und Dokumentation.

**1.1 FEATURE_STATUS.md ersetzen**
Das Dokument ist ein aneinandergehängter Session-Log. Ersetzen durch ein kompaktes Dokument
mit: aktueller Architekturzustand, bekannte offene Punkte, was stabil ist.
Das Original kann in `docs/archive/FEATURE_STATUS_legacy.md` archiviert werden.

**1.2 BuildingCreateShop.java aufteilen (1797 Zeilen)**
Kandidaten für Extraktion die schon natürliche Grenzen haben:
- `ShopHousekeepingOrchestrator` – Rack→Hut-Transfer-Logik (bereits teilweise in ShopBeltManager)
- `ShopResolverHealthCheck` – Resolver-Health- und Sync-Logik
- `ShopColonyEventHandler` – Colony-Tick-Handler-Logik
Ziel: BuildingCreateShop unter ~600 Zeilen.

**1.3 CreateShopMaintenanceCommands.java aufteilen (1844 Zeilen)**
Klare Gruppen: DiagnostikCommands, ResetCommands, TestHarnessCommands.
Je eine Klasse, MaintenanceCommands wird zum Router.

**1.4 CreateShopDeliveryChildLedgerEntry bereinigen**
20+ Felder mit reinem Getter/Setter-Boilerplate. Entweder als Record umschreiben (Java 16+)
oder auf die wirklich benutzten Felder reduzieren. Diagnosedaten die nirgends ausgewertet werden
entfernen.

**1.5 Resolver-Micro-Services konsolidieren**
Das Resolver-Package hat 51 Klassen. Kandidaten für Zusammenführung:
- `CreateShopStackMetrics` + `CreateShopDeliveryOriginMatcher` → `CreateShopDeliveryUtils`
- `CreateShopResolverMessaging` → inline in `CreateShopAttemptResolveService` (nur dort genutzt)
- `CreateShopResolverRecheck` + `CreateShopResolverCooldown` → `CreateShopResolverTimers`
Ziel: ~35 Klassen statt 51, ohne Logikänderung.

---

### Phase 2 – Test-Coverage (Niedriges Risiko)

Vor jeder weiteren Änderung sicherstellen, dass die kritischen Flows getestet sind.

**2.1 RehydrateService-Szenarien testen**
Aktuell fehlen Tests für:
- Reload mit aktivem DELIVERY_CREATED-Request → kein Doppel-Order
- Reload mit orphaned InflightEntry → korrekte Bereinigung
- Token-Drift nach Resolver-Reassignment → PendingTracker nicht stuck

**2.2 5-Minuten-TTL-Ablauf testen**
`CreateShopPendingDeliveryTracker` nutzt einen Guava-Cache mit 5min TTL.
Wenn ein Request länger dauert (z.B. Courier-Stau), läuft der Cache ab → Request hängt fest.
Dieser Fall ist aktuell nicht getestet.

**2.3 Inflight-Serialisierungsroundtrip**
NBT save/load für `InflightEntry` mit allen Feldern – sicherstellen dass keine Daten verloren gehen.

---

### Phase 3 – State-Drift-Fix (Mittleres Risiko)

Das ist die eigentliche Wurzelbehandlung. Basis: Phase 2 muss grün sein.

**3.1 UUID in InflightEntry einführen**
Neues Feld `requestUuid` in `InflightEntry` und NBT-Serialisierung.
`recordInflight(...)` bekommt UUID als Parameter (vom Resolver übergeben).
Matching in `consumeOverdueNotices`, `reconcileInflight` etc. auf UUID-first umstellen.

**3.2 FlowState in NBT persistieren**
In `TileEntityCreateShop`: neue NBT-Section `FlowStates` → `Map<UUID, String (FlowState.name())>`.
`CreateShopRequestStateMachine` bekommt save/load-Methoden.
`BuildingCreateShop.saveAdditional` / `loadAdditional` bindet sie ein.

**3.3 RehydrateService vereinfachen**
Nach 3.1+3.2: RehydrateService liest FlowState aus NBT, prüft ob Request noch in MineColonies
existiert, bereinigt verwaiste Einträge. Die Heuristik-Ableitung entfällt.

**3.4 Inflight-Cleanup bei Cancel/Complete verdrahten**
Wenn ein Request terminal wird (Cancel oder Complete), sofortige Bereinigung des InflightEntry
per UUID. Kein Warten auf Timeout + Lost-Package-Dialog mehr.

---

### Phase 4 – Guard-Konsolidierung (Höheres Risiko)

Erst angehen wenn Phase 3 im Live-Betrieb stabil ist.

**4.1 Lost-Package-Fallback-Guards reduzieren**
Mit UUID-basiertem Matching fallen viele der String-Drift-Fallbacks weg:
- Component-Drift-Fallback in `consumeInflightByHandover`
- Duplicate-Segment-Caps
- Mehrfach-Matching bei gleichem Item
Das sind symptomatische Guards die nach Phase 3 obsolet sind.

**4.2 RehydrateService-Heuristik entfernen**
Der Heuristik-Pfad in `rehydrateAndFilter` (derive from outstanding need, merge pending counts)
kann nach Phase 3 entfernt werden. Nur noch: load from NBT + prune orphans.

**4.3 `CreateShopPendingDeliveryTracker` TTL erhöhen oder entfernen**
5 Minuten sind zu kurz für Spiele mit Courier-Engpässen oder Schlafzyklen.
Entweder: TTL auf 30 Minuten setzen, oder: den Cache durch eine einfache Map ersetzen
(da RehydrateService nach Phase 3 das Pruning beim Reload macht).

---

### Phase 5 – Server-Testing (Orthogonal zu allem)

**5.1 Dedicated-Server-Testlauf**
Der Mod läuft aktuell nur client-seitig getestet. Für Server gelten andere Threading-Garantien.
`ensureServerThread()`-Aufrufe in `CreateShopBlockEntity` deuten darauf hin, dass Threading
berücksichtigt wurde – aber es braucht echte Server-Tests.

**5.2 Multi-Shop-Isolation**
`PROJECT_TODO_LOCAL.md` nennt "multi-shop isolation hardening" als offenen Punkt.
Resolver-Instanzen sind instance-local (gut), aber SharedState zwischen mehreren Shops im
selben Colony ist noch nicht vollständig validiert.

---

## Priorisierung

| Phase | Risiko | Voraussetzung | Empfehlung |
|-------|--------|---------------|------------|
| 1 – Safe Cleanup | Niedrig | – | Sofort angehen |
| 2 – Tests | Niedrig | Phase 1 | Direkt danach |
| 3 – State-Drift-Fix | Mittel | Phase 2 grün | Kernarbeit |
| 4 – Guard-Konsolidierung | Hoch | Phase 3 stabil | Später |
| 5 – Server-Testing | Mittel | Phase 3 | Parallel zu Phase 4 |

---

## Was wir NICHT anfassen

- MineColonies-Request-Lifecycle-Internals (kein Mixin, kein Reflection) – Prinzip beibehalten
- Create-Network-Facade (funktioniert, niedriges Änderungsrisiko)
- Blueprints und Belt-Placement-Logik (separates System, kein Drift-Problem)
- Serialisierungs-IDs (Breaking Change, nur mit expliziter Migration)
