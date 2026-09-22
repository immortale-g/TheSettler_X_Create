# Backport auf Minecraft 1.20.1

Stand: 2026-09-19. **Nicht begonnen.** Dieses Dokument ist die Entscheidungsgrundlage und der
Arbeitsplan, falls der Port gemacht wird.

Anlass sind wiederholte Nachfragen von Spielern nach einer 1.20.1-Version.

## 1. Rahmenbedingung, die alles andere bestimmt

Auf 1.20.1 existiert NeoForge nicht als eigenständige API. Das Artefakt `net.neoforged:neoforge`
beginnt erst bei `20.2.12-beta`. Die 1.20.1-Linie liegt unter `net.neoforged:forge`, letzte
Version **1.20.1-47.1.106**, seit Ende 2023 eingefroren. Ihr Universal-Jar enthält 1156 Klassen,
alle unter `net/minecraftforge`, keine einzige unter `net/neoforged`. Der Paket-Rename kam erst
mit 1.20.2.

Konsequenz: Der Loader kann NeoForge heißen, der Quellcode importiert trotzdem
`net.minecraftforge.*`. Alle 1.20.1-Jars der Abhängigkeiten tragen eine klassische
`META-INF/mods.toml`, nicht `neoforge.mods.toml`.

Zweite Konsequenz: Wir setzen auf einen Loader auf, der keine Fixes mehr bekommt.

## 2. Verfügbarkeit der Abhängigkeiten

Alle Kernabhängigkeiten existieren für 1.20.1 und werden aktiv gepflegt. Create 6 wurde
zurückportiert, das komplette Package-, Packager-, Stock-Ticker- und Factory-Board-System ist
dort vorhanden. Damit entfällt der Grund, aus dem der Port unmöglich wäre.

| Mod | 1.21.1 (aktuell) | 1.20.1 |
|---|---|---|
| MineColonies | 1.1.1264 | 1.1.1276 |
| Structurize | 1.0.807 / 1.0.808 | 1.0.818 |
| BlockUI | 1.0.199 | 1.0.194 |
| Create | 6.0.10 | 6.0.8 |
| Create Factory Logistics | 1.6.0 | 1.4.7 |

CFL 1.4.7 enthält `create_factory_abstractions` mit
`GenericLogisticsManager.broadcastPackageRequest` in identischer Signatur, inklusive
`boolean`-Rückgabe. Die reflektive Bridge würde also unverändert funktionieren.

## 3. Messergebnisse

Alle Zahlen stammen aus `tools/apidiff` und sind reproduzierbar:

```
gradlew apiDiff -PapiDiffArgs="surface 1.20.1"
gradlew apiDiff -PapiDiffArgs="vanilla 1.20.1"
gradlew apiDiff -PapiDiffArgs="behaviour 1.20.1 --depth 2"
gradlew apiDiff -PapiDiffArgs="implements 1.20.1"
```

### Mod-APIs: praktisch vollständig

87 von 87 referenzierten Upstream-Klassen vorhanden, 170 von 197 Aufrufen signaturgleich, 6 nur
namensgleich (Signaturdrift durch `HolderLookup.Provider`, `RegistryFriendlyByteBuf`,
`ItemStackHandler`), 20 über Vererbung außerhalb des geprüften Raums nicht entscheidbar.

### Verhalten: 3,4 Prozent Drift

293 Methoden zwei Ebenen tief verglichen, 230 bitgleich, 53 nicht vergleichbar (abstrakt oder
Interface), **10 mit echter Abweichung**:

1. **Create speichert Paketdaten in NBT statt DataComponents** (6 Treffer). `PackageItem`
   benutzt auf 1.20.1 die Schlüssel `Address`, `OrderId`, `LinkIndex`, `IsFinalLink`, `Index`,
   `IsFinal`, `OrderContext`, `Fragment`. `FilterItemStack.of` ruft `trimFilterTag` statt
   `trimFilterComponents`.
2. **Interne Umbauten** (3 Treffer). `PackagerBlockEntity.getAvailableItems` delegiert auf
   1.20.1 an eine Überladung statt das `InventorySummary` selbst zu bauen.
   `WiFiEffectPacket.send` geht über `AllPackets.sendToNear` statt über Catnips `NetworkHelper`.
   `AbstractTextElement.setText` in BlockUI weicht ab.
3. **Structurize registriert Placement-Handler anders**. `PlacementHandlers.add` hat 19 statt 5
   Instruktionen. Betrifft direkt unsere Create-Placement-Handler aus 0.3.1.

### Eigene Typen auf Upstream-Basis: 2 Treffer

19 unserer Klassen erweitern oder implementieren einen Upstream-Typ. Gegen 1.20.1 fehlen genau
zwei abstrakte Methoden, beide in `menu/ColonyGaugeSetItemMenu` gegenüber Creates
`GhostItemMenu`: `createGhostInventory` erwartet dort einen `net.minecraftforge`-`ItemStackHandler`
und `createOnClient` einen `FriendlyByteBuf` statt eines `RegistryFriendlyByteBuf`. Beides
erledigt sich in Phase 2 und 3, es ist kein eigener Posten.

### Vanilla: 11 fehlende Klassen

100 von 111 referenzierten Minecraft-Klassen existieren in 1.20.1. Die 11 fehlenden verteilen
sich auf drei Themen: DataComponents (`DataComponents`, `DataComponentType`, `CustomData`),
Netzwerk-Codecs (`CustomPacketPayload`, `StreamCodec`, `ByteBufCodecs`,
`RegistryFriendlyByteBuf`) und zwei Einzelfälle (`ItemInteractionResult`,
`Item.TooltipContext`).

### Loader: 49 Klassen

37 davon sind Umbenennungen, 12 brauchen echten Umbau.

### Betroffener Code

**79 von 210 Quelldateien.** Die Kernlogik, also Request-System, Resolver, Stock-Ledger,
Courier-Handling, AI-States und Shop-Policy, ist nicht betroffen. **175 von 179 Testklassen**
(9.891 Zeilen) laufen unverändert weiter, weil sie gegen unsere eigene Logik testen.

## 4. Strategie

Drei Möglichkeiten, den Zweig zu führen:

| Variante | Kosten | Bewertung |
|---|---|---|
| Einmalig portieren, danach einfrieren | Nur der Port | **Empfohlen.** Spieler bekommen eine funktionierende Version, wir binden keine laufende Zeit. Die 1.20.1-Version bleibt auf dem Stand des Ports. |
| Zweig mitführen, regelmäßig aus develop mergen | Der Port plus dauerhaft grob ein Drittel der Entwicklungszeit | Bei 30.000 Zeilen in aktiver Entwicklung und einer Person nicht tragbar. Jeder Merge trifft 79 Dateien mit abweichender API. |
| Abstraktionsschicht für beide Versionen in einer Codebase | Umbau des gesamten Plattformcodes plus dauerhafte Disziplin | Lohnt sich erst bei mehreren Entwicklern. |

Bei der empfohlenen Variante gehört in die README, welchem Stand die 1.20.1-Version entspricht
und dass sie keine neuen Features bekommt.

**Vorbedingung:** Der Port sollte erst nach 1.0.0 beginnen. Solange die Punchlist offen ist und
sich die Persistenz noch ändert, würde der Zweig beim Einfrieren einen unfertigen Stand
konservieren.

## 5. Arbeitsplan

Die Phasen bauen aufeinander auf. Vor Phase 2 kompiliert nichts, das ist normal und kein Grund,
die Reihenfolge zu ändern.

### Phase 1: Buildsystem (1 bis 2 Tage)

- NeoGradle auf `net.neoforged:forge:1.20.1-47.1.106` umstellen, Parchment auf 1.20.1.
- Java-Toolchain von 21 auf 17.
- `neoforge.mods.toml` zu `mods.toml`, Feldnamen anpassen.
- Abhängigkeitsversionen in `gradle.properties` auf die 1.20.1-Stände.
- Repositories bleiben, nur Koordinaten ändern sich.

**Fertig, wenn** `gradlew dependencies` auflöst und ein leerer Mod startet.

### Phase 2: Loader-Umbenennungen (1 bis 2 Tage)

Mechanisch, breit gestreut, kein Nachdenken:

- `net.neoforged.neoforge.*` zu `net.minecraftforge.*`, `net.neoforged.bus.api` zu
  `net.minecraftforge.eventbus.api`, `net.neoforged.fml` zu `net.minecraftforge.fml`.
- `ModConfigSpec` zu `ForgeConfigSpec`, `NeoForge` zu `MinecraftForge`,
  `IMenuTypeExtension` zu `IForgeMenuType`.
- `DeferredHolder`, `DeferredItem`, `DeferredBlock` alle zu `RegistryObject`, betrifft
  `init/ModBlocks`, `ModItems`, `ModBlockEntities`, `ModMenus`.
- `ServerTickEvent.Post` zu `TickEvent.ServerTickEvent` mit Phasenprüfung.
- 25 Aufrufe `ResourceLocation.fromNamespaceAndPath` zurück auf den Konstruktor.

**Fertig, wenn** nur noch Fehler aus den Phasen 3 bis 5 übrig sind.

### Phase 3: Netzwerk (1 bis 2 Tage)

Betrifft die 7 Dateien in `network/`.

- `SimpleChannel` statt `PayloadRegistrar`, Registrierung in `ModNetwork`.
- Die 6 Payload-Records bekommen `encode`/`decode`-Methoden statt `StreamCodec`.
- `IPayloadContext` zu `NetworkEvent.Context`, Thread-Wechsel über `enqueueWork`.

**Fertig, wenn** alle Payloads in beide Richtungen laufen, geprüft im Dev-Client.

### Phase 4: DataComponents zu NBT (2 bis 3 Tage)

Der inhaltlich heikelste Block, 55 Stellen. Betrifft `ItemStackDataUtil`,
`init/ModDataComponents`, `block/ColonyGaugeBlock`, `block/ColonyGaugeBlockItem`,
`create/CreatePackageBridge`, `item/StockLinkLinkerItem`,
`minecolonies/block/BlockHutCreateShop`.

- `ModDataComponents` entfällt, stattdessen NBT-Schlüssel als Konstanten.
- `GAUGE_ORDER_OPEN` wird ein Boolean-Tag.
- `ItemStackDataUtil` auf `getOrCreateTag` umstellen, das ist der zentrale Punkt.
- **Create-spezifisch:** Wo wir Paketadressen oder Orders lesen und schreiben, benutzt Create auf
  1.20.1 die NBT-Schlüssel aus Abschnitt 3. Der `CreatePackageBridge` muss das berücksichtigen,
  hier reicht keine reine Umbenennung.

**Fertig, wenn** ein Gauge über Weltneuladen hinweg seinen Zustand behält und Pakete korrekt
adressiert ankommen.

### Phase 5: Capabilities und BlockEntity-Serialisierung (1,5 bis 2 Tage)

- `ForgeCapabilities.ITEM_HANDLER` mit `LazyOptional` statt `BlockCapability`, betrifft
  `blockentity/ColonyPackagerBlockEntity`, `minecolonies/building/ShopRackIndex`,
  `minecolonies/compat/MineColoniesPickupCompat`. Achtung auf Invalidierung, das Modell
  unterscheidet sich vom 1.21-Modell.
- `loadAdditional`/`saveAdditional` ohne `HolderLookup.Provider`, 42 Stellen in 18 Dateien.
- `ItemStack.parseOptional`/`saveOptional` durch die 1.20.1-Entsprechungen ersetzen.

**Fertig, wenn** Racks und Packager ihre Inventare über einen Serverneustart behalten.

### Phase 6: Client und Reste (1 bis 1,5 Tage)

- `RegisterMenuScreensEvent` zu `MenuScreens.register` im `FMLClientSetupEvent`.
- `ConfigurationScreen` gibt es nicht. Entweder eigener Screen oder Config-UI weglassen,
  Entscheidung beim Port.
- `ItemInteractionResult` zu `InteractionResult`, `Item.TooltipContext` entfällt.
- `Attributes.BLOCK_INTERACTION_RANGE` zu `ForgeMod.BLOCK_REACH`.
- **Structurize:** `PlacementHandlers.add` neu verdrahten, betrifft
  `create/compat/CreatePlacementHandlers` und `CreateBeltPlacementHandler`.
- BlockUI-Screens gegen 1.0.194 prüfen, `AbstractTextElement.setText` weicht ab.

### Phase 7: Tests und CI (1 Tag)

- Die 4 betroffenen Testklassen anpassen, die übrigen 175 sollten unverändert laufen.
- `testStructurizeModernApi` entfällt, 1.20.1 hat die Frage nicht.
- `testFactoryLogistics` gegen CFL 1.4.7 richten.
- `junit-fml` gegen die 1.20.1-Variante prüfen, hier ist Reibung zu erwarten.

### Phase 8: Ingame-Test (3 bis 5 Tage)

Der Teil, der sich nicht planen lässt. Die Tester-Checkliste aus `docs/testing` durchspielen,
mit Schwerpunkt auf den Stellen, an denen uns 1.21.1 schon Bugs geliefert hat: Verhalten nach
Weltneuladen, Inflight-Verfall, doppelte Bestellungen, Courier-Übergabe.

## 6. Aufwand

| Phase | Aufwand |
|---|---|
| 1 Buildsystem | 1 bis 2 Tage |
| 2 Loader-Umbenennungen | 1 bis 2 Tage |
| 3 Netzwerk | 1 bis 2 Tage |
| 4 DataComponents | 2 bis 3 Tage |
| 5 Capabilities und Serialisierung | 1,5 bis 2 Tage |
| 6 Client und Reste | 1 bis 1,5 Tage |
| 7 Tests und CI | 1 Tag |
| 8 Ingame-Test | 3 bis 5 Tage |
| **Summe** | **11,5 bis 18,5 Arbeitstage** |

## 7. Offene Risiken

1. **Laufzeitverhalten.** Die statische Analyse erreicht Event-Reihenfolge, Tick-Timing und den
   Moment, in dem MineColonies einen Request schließt, grundsätzlich nicht. Genau diese Klasse
   von Fehlern hat uns in 0.3.x und 0.6.0 beschäftigt. Deshalb sind für Phase 8 mehr Tage
   angesetzt als für jede andere Phase.
2. **53 nicht vergleichbare Methoden.** Abstrakte und Interface-Methoden haben keinen Körper.
   Man könnte ihre Implementierungen auflösen und mitvergleichen, das wäre ein Ausbau von
   `tools/apidiff`.
3. **Datenseite ungeprüft.** Rezept-JSON-Schema, Tags und das Blueprint-Format sind nicht
   vermessen. Ob Structurize 1.0.818 unsere beiden Shop-Blueprints unverändert lädt, weiß
   niemand, bevor es jemand versucht.
4. **Eingefrorener Loader.** Tritt in NeoForge 47.1.106 ein Fehler auf, gibt es keinen Upstream
   mehr, der ihn behebt.

## 8. Wenn der Port nicht gemacht wird

Dann ist die belegbare Begründung für Nachfragen: 1.20.1 gibt es nur gegen die Forge-API, deren
1.20.1-Linie seit Ende 2023 eingefroren ist. Es scheitert nicht an Create oder MineColonies, die
sind dort vollständig verfügbar, sondern an der Loader-Lage und daran, dass ein zweiter Zweig
dauerhaft Entwicklungszeit bindet, die in 1.0.0 besser aufgehoben ist.
