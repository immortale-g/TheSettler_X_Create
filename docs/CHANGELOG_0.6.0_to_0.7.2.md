# Changelog: 0.6.0 to 0.7.2

Everything that changed for players since 0.6.0, which is what is on CurseForge today. Version 0.7.0 and
0.7.1 were never published, their changes are part of 0.7.2.

## 0.7.2

Fixes from a review of the 0.6.0 release, and the shop storage rework that followed. These were
counted as 0.7.0 and 0.7.1 while they sat on their own branches; neither number was handed out, so
everything ships as 0.7.2.

### Requests the shop can really fill

- The shop counted the same goods twice when deciding whether it could serve a request: once in the
  racks and once more through the pickup block, which only shows what the racks hold. It accepted
  requests it could not fill, so the colony's crafter never saw them and the request hung. It now
  counts the racks once.
- The hut inventory no longer counts as rack stock. What the shop owes its Colony Factory Gauges is
  taken off the racks only as far as it spilled over from a full hut inventory; goods that sit in the
  hut were already outside the rack count.
- A hopper or funnel on the pickup block can no longer pull goods that are reserved for a colony
  request. It only gets what the racks hold beyond the reservations.

### Colony Factory Gauge

- A shop serves the order whose goods have arrived, instead of only the oldest one. An order waiting
  on a crafter no longer holds back orders behind it that a courier already filled.
- An order the colony can only fill in part no longer leaves the gauge waiting forever. The colony
  hands over what a drained warehouse has left and closes the order; the gauge now learns that and
  asks again, instead of sitting satisfied with a storage that never reaches its target.
- Large orders travel in several packages. A package holding more than nine stacks could not be
  saved, so it disappeared together with whatever chunk it was in when the world was written.
- A package that was split over several slots is now counted in full on arrival, not just its first
  slot.

### Shop settings

- Amounts like `16.1k` are accepted again. The field wrote numbers it then refused to read back,
  which also hit the amount the picker pre-fills for an item that is already guarded.
- At thirty network minimums, the Add button opens the picker for the kinds already guarded, so one
  of them can be raised or lowered. It previously did nothing at all and gave no reason.
- A `permaMinBuildingLevel` set by hand in the config carries over to `gaugeMinBuildingLevel`, which
  replaced it in 0.6.0. Without that, a shop level lowered to 1 was silently back at 2.

### Shop stock

- A shop with many open orders for the same item no longer orders some of them a second time. The
  shop keeps at most two notes per item so its reports stay readable, and the amount of a note it
  folds away is now added to the note it keeps instead of vanishing from what counts as on its way.

### Shop storage

- What a courier brings to a shop goes into the hut's own inventory instead of the racks. The
  racks are where Create delivers, and a rack a courier filled is space the stock network cannot
  use: an order from Create then waits on its way until something frees the rack up again. With
  two racks on a shop that was quick to hit.
- A courier still uses the racks when the hut inventory is full, and the shopkeeper carries those
  goods back to the hut on its next round rather than after the usual five-minute wait.
- A courier that finds the shop full keeps its goods and says so, instead of taking a stack out of
  a rack to make room. That stack could be goods a gauge or a citizen was already waiting for.
- A Colony Factory Gauge takes its goods from the hut inventory first and from the racks after, so
  a world that still has gauge goods lying in its racks empties them out.
- The shopkeeper keeps the rack the packager unpacks into clear. Create unpacks an arriving
  delivery into exactly one rack, and once that one is full nothing else arrives however much room
  the other racks have. Below five free slots the shopkeeper carries goods over to the others. The
  threshold is `arrivalRackMinFreeSlots` in the config; 0 turns it off.
- After Create could not deliver for want of room, the shopkeeper no longer waits the usual five
  minutes before moving unreserved goods out of the racks into the hut, where a courier can pick
  them up.
- A shop whose pickup priority is set to zero now says so in the message about the full shop.
  It used to suggest assigning more couriers, when no courier was ever called for.

### Bug reports

- Problems are logged whether or not `debugLogging` is on. Everything the mod caught and could not
  handle used to be written only as part of the debug trace, so a player who had turned the trace
  off - as the README suggests once testing is done - got an empty log out of a shop that had
  stopped working. A delivery that never finishes, a gauge order lost when the world is loaded, a
  config value that could not be read: each is now a warning tagged `[CreateShop][problem]`, saying
  what broke and what you will notice in game. Searching the log for that tag is the fastest way to
  a useful report. The trace itself still switches off.

### Under the hood

- What a shop owes its Colony Factory Gauges is kept in the packing list rather than as a
  reservation on its racks. Gauge goods wait in the hut inventory since the last change, where a
  rack reservation says nothing about them; they are held back from a pickup and from the shop's
  own deliveries exactly as before.
- The courier a pickup is booked against is found again after that courier is dismissed and rehired.
- The mod list shows a description, the authors and links to the project and its issue tracker,
  rather than the mod template's placeholder text.
- The mod list shows a logo next to the entry.
- Cancelling a lost package recognises a request graph MineColonies has already broken by where
  the failure came from, not by the wording of the error message. The old check would have
  stopped working, without a word, on the first MineColonies or Java version that phrased that
  message differently, and the shop would have kept retrying a cancel that cannot succeed.
- The shop's debug log asks MineColonies for what it prints instead of guessing method names. Ten
  of those guesses named methods MineColonies has never had, so those lines had said `<unknown>`
  since the first build: what a courier is carrying and where it stands, which resolver took a
  delivery, and how much of an item a request still wants. The courier's own queue is now read
  the way the rest of the mod reads it, without handing out warehouse work as a side effect.
- A bug report from a shop with a full storage now shows what the shopkeeper's entity was doing.
  That line had been dropped by an earlier cleanup and was never printed again.
- If a MineColonies release ever takes away the field the shop uses to clear stale shopkeeper
  dialogs, the log says so once, with the MineColonies version. It used to fail silently, leaving
  dialogs nobody asked for on the shopkeeper.
- Everything the mod knows about Create, MineColonies and BlockUI only by name is now checked
  against their jars while the mod is built: the Create blocks the colony builder is taught to
  place, the two tags a belt is rebuilt from, the MineColonies textures the shop's tabs are drawn
  on, every element in those tabs, and every line of text the mod shows. Each of those used to
  break quietly on an update, be noticed by a player, and cost a hotfix.
