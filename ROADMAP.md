# TheSettler_x_Create – Cleanup & Improvement Roadmap

Erstellt nach Code-Analyse, Juni 2026. Der Analyseteil unten ist der Stand von damals und wird
bewusst nicht umgeschrieben, damit die Begründungen nachvollziehbar bleiben.

## Status, Stand 2026-09-19

| Phase | Stand |
|-------|-------|
| 1 – Safe Cleanup | Abgeschlossen |
| 2 – Test-Coverage | Abgeschlossen |
| 3 – State-Drift-Fix | Abgeschlossen, Systemgrenze bei `DELIVERY_CREATED` gezogen (`e2387bd`) |
| 4 – Guard-Konsolidierung | Abgeschlossen |
| 5 – Server-Testing | Läuft weiter, keine abschließende Validierung |

Danach nicht mehr als nummerierte Phasen geführt: 0.3.0-Release mit Colony Gauge und Packager,
Placement-Handler als 0.3.1, `BuildingCreateShop`-Refaktor in `Shop*`-Collaborators, Konsolidierung
der Create-Logistik (`CreateLogisticsBridge`, `CreatePackageBridge`), Clean-Code-Audit auf `develop`
(damals `fix/pre-1.0-hardening`).

In 0.3.2 kamen vier Korrekturen am Request-Lebenszyklus dazu: Restbedarf berücksichtigt gelieferte
Mengen, der Tooltip-Crash des Network Link Tuners, und zwei entfernte Eingriffe in fremde
Kurier-Tasks (Force-Finish und Ongoing-Marker).

0.3.3 gibt den Abschluss von Parent-Requests an MineColonies zurück. Fertige Lieferungs-Childs
werden nicht mehr abgekoppelt, `resolveRequest` schließt nur bei vollständig geliefertem Bedarf, und
der Pending-Tracker verliert seinen Zustand nicht mehr fünf Minuten nach dem Anlegen.
Außerdem läuft 0.3.3 mit alten und neuen Structurize-Placement-Handlern (ab 1.0.808) und mit der
geänderten Pickup-API von MineColonies 1.1.1368. Die Abhängigkeiten sind fest über Maven gepinnt, ein
täglicher Workflow testet gegen die neuesten Releases.

0.3.4 hält Pickup-Reservierungen am Leben, solange ihr Request offen ist. Vorher verfielen sie fest
nach fünf Minuten. Große Requests, die stackweise ausgeliefert werden, bestellten den Rest dann
erneut beim Create-Netzwerk. Nach dem Laden bekommen gespeicherte Reservierungen eine frische Frist.

0.3.5 hört auf, aus der Diagnose heraus Kurieraufträge zu verteilen. `JobDeliveryman.getCurrentTask()`
zieht bei einem Kurier ohne Aufgaben neue Aufträge aus der Warehouse-Queue; der Shop rief es bei jeder
Prüfung eines offenen Lieferauftrags für alle Kuriere auf. Gelesen wird jetzt nur noch die Task-Queue.

0.6.0 ist am 2026-09-19 erschienen, das erste Release seit 0.3.6, und bringt 0.4.0, 0.5.0 und die
Shop-Versorgungsregeln auf einmal: Sammel-Lieferungen und das Bestands-Ledger (0.4.0), der
Unterwegs-Pool mit Bestellungen, die dem Shop gehören (0.5.0), und darauf aufbauend die
Versorgungsregeln pro Shop (Sperrliste, Netz-Mindestbestand), das Gauge-Crafting und die
überarbeiteten Hütten-Reiter mit eigenem Auswahlfenster und eigenen Icons. `debugLogging` bleibt bis
1.0.0 eingeschaltet.

Danach ein Review des Releases, abgearbeitet auf `fix/post-0.6.0-review` und seit dem 2026-09-21 in
`develop` (Version 0.7.1). Drei Befunde im Gauge-Versand: der Shop bediente nur den Kopf seiner Packliste und hielt
damit jede Bestellung dahinter auf, eine von der Kolonie kurz geschlossene Bestellung ließ den Gauge
ewig auf Ware warten, die niemand mehr schuldet, und ein Paket nahm die ganze Menge in einen Slot,
was beim Speichern zerbricht. Dazu Kleineres: das Mengenfeld las seine eigene Schreibweise nicht
zurück, der Add-Knopf war am 30er-Limit tot, `permaMinBuildingLevel` wurde beim Umbenennen still
zurückgesetzt, und der Kurier-Token-Cache wurde stale. Außerdem Metadaten für die Modliste, ein
CHANGELOG und die fehlenden deutschen Texte.

## Plan ab 2026-09-13

### Branches

- `master` ist die Release-Linie. Hotfixes zweigen von `master` ab, werden dort released und danach
  nach `develop` gemergt.
- `develop` ist die Entwicklungslinie (bis 2026-09-13 `fix/pre-1.0-hardening`). Sie enthält die
  1.0-Punchlist, den Clean-Code-Audit und alle 0.3.x-Hotfixes. Feature-Branches zweigen von
  `develop` ab und gehen dorthin zurück.
- Ein Release ist ein Merge von `develop` nach `master` mit Tag.

### 0.4.0: große Aufträge schneller ausliefern

Erledigt und mit 0.6.0 erschienen. Vorher legte der Shop pro Request immer nur eine Delivery mit
einem Stack an und wartete, bis sie abgeschlossen war. Große Aufträge laufen dadurch Tour für Tour nacheinander, und mehrere Kuriere
helfen nicht. Das Warehouse von MineColonies legt dagegen alle Deliveries auf einmal an.

- Extra-Child-Recovery abtrennen (auskommentiert, nicht gelöscht). Sie behandelt jedes zweite aktive
  Child als Fehler und würde parallele Deliveries sofort wieder abräumen.
- Reservierungen pro Request über mehrere Items und Mengen (heute ein Item pro Request, Tag-Requests
  verlieren dadurch Reservierungen). Bestandsformeln an einer Stelle, mit echten Unit-Tests.
- Deliveries starten an der Shop-Hütte, alle Stücke werden sofort angelegt. MineColonies bündelt sie
  pro Kurier und verteilt sie auf mehrere Kuriere.
- Reservierungen werden bei der Abholung verbraucht, sofern sich die Abholung verlässlich beobachten
  lässt, sonst bei der Ablieferung.
- Signatur-Match in `finalizeOrphanDeliveryChild` entfernen.
- `pickupConfirmedAtTick` erst setzen, wenn die Ware die Hütte wirklich verlässt (zwei
  Recovery-Pfade hängen daran). Die Kurier-Aufgabe allein zählt nur noch, wenn die Reservierung des
  Requests schon aufgebraucht ist (Deliveries, die vor einem Reload eingesammelt wurden).
- Housekeeping: Unreservierte Ware erst nach 5 Minuten (Config) in die Hütte tragen, Alter wird
  gespeichert. Ein Warehouse-Pickup nimmt keine Rack-Ware und nichts Reserviertes mehr mit.
- Übergangsschutz: Deliveries aus Welten vor 0.4.0 (Start am Rack) verbrauchen ihre Reservierung
  weiter bei der Ablieferung.
- OP-Befehle `/thesettlerxcreate tracking-reset <colonyId> [scope]` und `tracking-reset-all [scope]`
  setzen das Shop-Tracking zurück (reservations, inflight, stock-ages, flow-states, gauge, runtime),
  ohne Requests abzubrechen. Jede neue Tracking-Art bekommt einen eigenen Scope.

### 0.5.0: Bestellungen gehören dem Shop

Erledigt und mit 0.6.0 erschienen (Branch `feat/inflight-pool`, auf `feat/stock-observer` aufbauend).

- Inflight-Buchhaltung als `InflightBook` im Paket `stock/` mit Unit-Tests, Speicherformat unverändert.
- Ankunftserkennung robust: Jede Rack-Bewegung, die der Shop kennt (Hütten-Tür, Housekeeping,
  Output-Block, Übergaben, Kurier-Dumps), verschiebt den Vergleichswert mit.
- Reservieren erst, wenn Ware im Rack ankommt (vor der Planung jedes Kolonie-Ticks), gedeckelt auf
  freien Rack-Bestand ohne Gauge-Reservierungen. Unterwegs-Ware steht nur noch im Inflight-Ledger.
- Inflight-Einträge haben einen optionalen Besitzer. Endet ein Request, wird die Bestellung abgekoppelt
  statt gelöscht, und ein Folge-Request übernimmt sie, statt neu zu bestellen. Besitzerlose Bestellungen
  verfallen nach dem Inflight-Timeout still.
- Eine einzige Bestellstelle (`CreateShopNetworkOrderService`) für ersten Versuch und Nachschub, die
  Ware schon beim Einreihen als unterwegs erfasst. Aufgegebene Broadcasts streichen genau diese
  Bestellung.
- Nachschub und neue Deliveries auch bei offenen Delivery-Children (`OpenDeliveryPlan`): offene
  Deliveries zählen beim Bestellen als gedeckt, bei neuen Deliveries werden nicht abgeholte abgezogen.
- Nicht mehr offen: der Inflight-Cleanup pro abgeschlossenem Child (I-3) und die Warehouse-Zählung
  (R-4, wird für den Shop von MineColonies nicht aufgerufen, jetzt trotzdem konsistent).
- Colony Factory Gauge bleibt beim Reservieren beim Bestellen; das löst die Trennung von Racks und
  Hütte in 1.0.

### 0.6.0: Versorgungspolitik und Gauge-Fertigung

Erledigt und mit 0.6.0 erschienen (Branches `feature/shop-supply-policy` und
`feature/gauge-colony-crafting` über `integration/supply-policy-and-crafting`).

- Pro Shop eine Sperrliste, welche Items die Kolonie **nicht** aus dem Create-Netz abrufen darf
  (MineColonies' `ItemListModule`, leere Liste = alles erlaubt).
- Pro Shop ein Mindestbestand je Item, der im Create-Netz bleibt. Hält nur zurück, bestellt nichts
  nach. Beides wirkt über `ShopSupplyPolicy` in `CreateShopStockResolver`, eigene Shop-Abläufe
  bleiben ungefiltert.
- Die Gauge fragt auch nach Dingen, die die Kolonie herstellen könnte: volle Menge als
  `minimumCount`, damit MineColonies die Restmenge ans Crafting gibt, plus Vorabprüfung, ob
  überhaupt jemand das Rezept kennt.
- `CreateShopChainOriginGuard` verhindert den Warenkreisel: kein Shop bedient das, was ein Shop bei
  der Kolonie bestellt hat, solange es um dasselbe Item geht.
- Einlieferung und Racks sind getrennt: was ein Kurier bringt, geht ins Hütten-Inventar, die
  Racks bleiben der Rückfall für eine volle Hütte und der Shopkeeper räumt sie ohne Wartezeit
  wieder frei. Kein Stapel wird mehr getauscht, und der Output-Block liest die Hütte zuerst.
- Der Flaschenhals der Einlieferung ist bewirtschaftet: Create packt in genau ein Rack aus, und
  der Shopkeeper hält dieses Rack unter `arrivalRackMinFreeSlots` freien Plätzen frei, indem er
  Ware in die übrigen Racks trägt. Bei einem Capacity-Stall entfällt zusätzlich die
  Housekeeping-Wartezeit, und die Stall-Meldung nennt eine auf null gestellte Abholpriorität.
- Eine Gauge-Bestellung ist keine Rack-Reservierung mehr. Was der Shop seinen Gauges schuldet,
  steht in der Packliste; der Pickup lässt genau diese Menge stehen, und der Resolver zieht sie vom
  Rack-Bestand ab. Das Reservierungs-Ledger neben dem Pickup-Block führt damit nur noch die
  Create-Requests, und die Sonderfälle dafür (Inflight-Ankünfte, Reservierungs-Keep-Alive) sind
  weg.

### Offen für 1.0

Stand 2026-09-22, jeder Punkt gegen den Code geprüft. Es ist nur noch Ingame-Validierung offen;
alles andere aus dieser Liste steht unten in den Ergebnis-Abschnitten.

- Dedicated-Server-Test und mehrere Shops in einer Colony.
- Ingame-Test der Review-Fixes aus `fix/post-0.6.0-review`, vor allem der Gauge-Versand: zwei Gauges
  am selben Shop, einer davon auf einen Crafter wartend, und eine Bestellung über mehr als ein Paket.
- Diagnose-Marker `MC_QUEUE_DEQUEUED_WITHOUT_TERMINAL` im Spiel sichten.

Im Release-Commit selbst: `debugLogging` auf `false`, zusammen mit
`ConfigDebugLoggingDefaultGuardTest` und dem README-Absatz.

### Am 2026-09-19 aus dieser Liste gestrichen

Nicht an diesem Tag erledigt, sondern beim Abgleich mit dem Code als längst erledigt vorgefunden.

- Requests, die mindestens `minimumCount` erhalten haben, hängen nicht mehr fest:
  `ShopStockAccounting.canCloseShort` schließt sie, sobald nichts mehr reserviert, im Rack oder
  unterwegs ist (`CreateShopFinishShortOfCountFmlTest`).
- `onHutItemsTaken` ordnet nicht mehr blind zu: `CourierOngoingDeliveries` liest die laufenden
  Lieferungen aus MineColonies' eigenem Datenspeicher. Grenze bleibt der Fall zweier Kuriere, die im
  selben Moment dasselbe Item am selben Shop holen, weil die Entnahme keinen Akteur trägt.
- `ShopGaugeQueue` hat Verhaltenstests (19 unter FML), dazu `GaugePackageSelection` und
  `CreatePackageBridge`.

### Am 2026-09-21 aus dieser Liste gestrichen

- `attemptResolve` ist umgebaut. Der Hauptteil war schon am 2026-09-16 in drei Schritte zerlegt
  worden (`b427b2a`, Eligibility, Bestandsplan, genau ein Ausgang); die Zeile hier hatte den Stand
  nur nicht nachgezogen. Am 2026-09-21 kamen die letzten zwei Stellen dazu: `checkEligible` gibt
  seine Torprüfungen an `isCancelled`, `isOpenForAnAttempt` und `findShopParts` ab, und das dreifach
  ausgeschriebene "bleibt pending" liegt jetzt in `keepPending`. `attemptResolve` selbst ist 27
  Zeilen, die längste Methode der Klasse ist `deliverFromRacks` mit gut 50.

### Am 2026-09-21 erledigt: S2 bis S5 des 0.7.1-Sammelstands

Die vier Zeilen standen bis 2026-09-22 noch in der Liste oben, obwohl sie am 2026-09-21 abgearbeitet
wurden.

- Logo für die Modliste: selbst gezeichnet (`tools/icons/make_logo.py`), `logoFile` in der
  mods.toml, `ModsTomlMetadataGuardTest` prüft, dass die genannte Datei existiert.
- `InflightBook.compact()` streicht ein verdrängtes Segment nicht mehr, sondern faltet es in das
  älteste behaltene Segment des Tupels ein und meldet die gewachsene Menge erneut.
- Die Erkennung eines kaputten Request-Graphen hängt nicht mehr am Text der Fehlermeldung: die
  Minecraft-freie Klasse `StaleRequestGraphDetector` entscheidet nach Typ und oberstem Stackframe
  (Text nur noch, wenn der Stacktrace leer ist), Canceller und Command teilen sie sich.
  `MineColoniesStaleRequestGraphContractFmlTest` meldet im Nightly, wenn die beiden Wurfstellen
  verschwinden.
- Die drei 0.5.0-Services haben Verhaltenstests mit geladenem FML statt Quelltext-Guards
  (`CreateShopNetworkOrderServiceFmlTest`, `CreateShopPickupObservationServiceFmlTest`,
  `CreateShopOpenDeliveryTopupServiceFmlTest`, dazu `CreateNetworkFacadeAbandonedOrderFmlTest`).

### Am 2026-09-22 erledigt: Reflection-Audit

Die Diagnose-Ausgaben fragen MineColonies wieder über dessen Interfaces statt über Methodennamen.
19 reflektive Aufrufe sind direkte Aufrufe geworden, einer war hinter einem `instanceof`-Zweig
unerreichbar und ist ersatzlos weg, 10 weitere sind gestrichen: sie nannten
Methoden, die es in MineColonies nicht gibt (`getState`/`isWorking` auf einem Job, die drei
`getCurrentRequest*`, `getEntityId`, `getPosition` auf `CitizenData`, `IRequest.getProvider`,
`getMinimalCount` statt `getMinimumCount`) und liefen seit dem ersten Commit ins `catch`. Zwei
Textvergleiche auf Fremdklassennamen sind `instanceof` geworden; der Vergleich auf
`"WarehouseRequestResolver"` übersah dabei still `WarehouseConcreteRequestResolver`.
`ShopCourierDiagnostics` liest die Kurier-Warteschlange über `getTaskQueue()`, nicht über
`getCurrentTask()` — das verteilt Lagerarbeit als Seiteneffekt (siehe 0.3.5). Nebenbei gefunden:
`logCitizenEntityDiagnostics` lief seit `f319807` nie, weil derselbe Commit den Marker entfernt
hat, auf den der Aufrufer prüft.

Übrig sind drei Reflection-Stellen, alle berechtigt und alle einmalig auflösend:
`CreateFactoryLogisticsCompat` (CFL ist optional), `BuildingCreateShop.SuperPickupRequest` (zwei
API-Varianten) und `CitizenData.citizenChatOptions` — letzteres jetzt in einem `static final`
Holder mit lautem `WARN` samt MineColonies-Version statt stillem Fehlschlag, und mit
`CitizenChatOptionsCompatTest` gegen das echte Jar abgesichert.
`ShopCourierDiagnosticsNoPrivateReflectionGuardTest` prüft jetzt auch auf `getMethod(` — dieser
blinde Fleck hatte die zehn toten Aufrufe überleben lassen.

### Am 2026-09-22 erledigt: Compat-Tests für jede String-Kopplung

Die offenen Versionsbereiche in der `mods.toml` sind nur ehrlich, wenn ein Bruch im Build auffällt.
Fünf neue Tests fragen dafür das echte Jar, alle an `check` und damit im Nightly gegen die neueste
MineColonies-Version:

- `CreateBlockIdCompatTest` -- die elf Create-Ids der Placement-Handler (Casings, Wellen, Zahnräder,
  Gurt, Gurt-Verbinder) müssen im Create-Jar eine Blockstate- oder Item-Model-Datei haben. Die Ids
  liest der Test aus dem Produktivcode; `CreatePlacementHandlers.compositeBlockRequiredItems()` und
  die drei Konstanten in `CreateBeltPlacementHandler` sind dafür paketsichtbar.
- `CreateBeltNbtCompatTest` -- `Length` und `Controller` müssen weiter als String-Konstanten in
  Creates `BeltBlockEntity` stehen, sonst puffert der Handler jedes Gurt-Segment für immer.
- `MineColoniesGuiTextureCompatTest` -- jeder fremde `<modid>:textures/...`-Pfad in unseren Layouts
  muss im Jar dieser Mod liegen. Der Test liest die Layouts selbst, eine Liste im Test gibt es nicht.
- `BlockUiLayoutSchemaTest` -- alle Layouts gegen `block_ui.xsd` aus dem BlockUI-Jar, mit
  `javax.xml.validation` aus dem JDK. Fängt umbenannte Elemente und Attribute auf einmal ab.
- `ShopLangKeyCompatTest` -- die 67 Schlüssel aus dem Java-Code und die 19 aus den Layouts: eigene in
  `en_us.json` und `de_de.json`, fremde in der Lang-Datei von MineColonies oder Minecraft.

Geteilt statt kopiert: `CompiledClassFacts` (die ASM-Abfrage aus `CourierOngoingDeliveriesCompatTest`)
und `ShopGuiLayouts` (findet die Layout-XMLs für alle drei Layout-Tests).

### Danach

- MineColonies-Followup-Muster: Der Shop setzt den Request auf RESOLVED, sobald die Ware reserviert im
  Rack liegt, legt die Deliveries in `getFollowupRequestForCompletion` an, und MineColonies schließt
  den Request selbst ab. Setzt 0.5.0 voraus und ist eine eigene Entscheidung, weil Teillieferungen dann
  der MineColonies-Semantik folgen.

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
`docs/agent/TODO_LOCAL.md` nennt "multi-shop isolation hardening" als offenen Punkt.
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
