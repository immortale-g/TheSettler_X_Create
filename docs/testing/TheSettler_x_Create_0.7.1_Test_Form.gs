/**
 * Creates the "Create Shop 0.7.1 Test Plan" Google Form in your Google Drive.
 *
 * 1. Open https://script.google.com and click "New project".
 * 2. Replace the code in Code.gs with this whole file and save.
 * 3. Select the function createTestPlanForm and click "Run".
 * 4. Allow access when Google asks (the script only creates this form).
 * 5. Open "Execution log": it prints the edit link and the link to send to testers.
 *
 * The scenario numbers S1 to S27 are the ones in docs/test_tasks_refactor.md. That file is the
 * source of truth; when it changes, change this generator in the same commit.
 */
function createTestPlanForm() {
  var form = FormApp.create('Create Shop 0.7.1 Test Plan');
  form.setDescription("0.7.1 collects the fixes found after 0.6.0. Most of them are in how the shop stores goods, how a Colony Factory Gauge is served and what a shop is allowed to draw from the Create network. We need two things from you: walk the new flows, and confirm that what worked in 0.6.0 still works. Mod file: thesettler_x_create-0.7.1.jar");
  form.setCollectEmail(false);
  form.setAllowResponseEdits(true);
  form.setProgressBar(true);
  var RESULTS = ['Pass', 'Fail', 'Not tested'];

  function addTest(id, title, tag, help) {
    form.addMultipleChoiceItem()
      .setTitle(id + '  ' + title + '  [' + tag + ']')
      .setHelpText(help)
      .setChoiceValues(RESULTS)
      .setRequired(true);
    form.addParagraphTextItem()
      .setTitle(id + ' notes / log lines')
      .setHelpText('What happened, time of the problem, relevant log lines.');
  }

  function addGridTest(id, title, tag, help, rows) {
    form.addGridItem()
      .setTitle(id + '  ' + title + '  [' + tag + ']')
      .setHelpText(help)
      .setRows(rows)
      .setColumns(RESULTS)
      .setRequired(true);
    form.addParagraphTextItem()
      .setTitle(id + ' notes / log lines')
      .setHelpText('What happened, time of the problem, relevant log lines.');
  }

  // Tester and environment
  form.addSectionHeaderItem().setTitle('Tester and environment')
    .setHelpText("Only test in a copy of your world.\nBack up the world, put the jar into the mods folder and remove the old version.\nIn config/thesettler_x_create-common.toml set debugLogging = true. Without it we can hardly trace a bug. Problems are written to the log either way, tagged [CreateShop][problem].\nFor every problem, save logs/latest.log (on a server the server log) before restarting, and note the time it happened.\nScreenshots of the shop task list, the racks, the hut inventory and the chat help more than long descriptions.");
  form.addTextItem().setTitle("Tester (name / Discord)").setRequired(true);
  form.addTextItem().setTitle("Date").setRequired(false);
  form.addTextItem().setTitle("Minecraft / NeoForge").setHelpText("1.21.1 / NeoForge 21.1.219 or newer").setRequired(false);
  form.addTextItem().setTitle("MineColonies").setHelpText("1.1.1264 or newer, please also test the latest version").setRequired(false);
  form.addTextItem().setTitle("Structurize").setHelpText("1.0.807 or newer").setRequired(false);
  form.addTextItem().setTitle("Create").setHelpText("6.0.10").setRequired(false);
  form.addTextItem().setTitle("Create: Factory Logistics (optional)").setHelpText("1.5.2 or newer if you have it installed").setRequired(false);
  form.addMultipleChoiceItem().setTitle("Singleplayer or server").setChoiceValues(['Singleplayer', 'Dedicated server']).setRequired(true);
  form.addTextItem().setTitle("Other mods").setRequired(false);

  // Pre-flight
  form.addPageBreakItem().setTitle('Pre-flight')
    .setHelpText("Set this up once before the scenarios:\n1. A world with one Warehouse, one Create Shop, free rack space and an assigned courier.\n2. The Create stock network holds the test item (create:shaft) in quantity.\n3. debugLogging = true.\n\nCommands used below (operator rights):\n/thesettlerxcreate reset_live_state\n/thesettlerxcreate run_live_test requests=1 amount=8 item=create:shaft\n/thesettlerxcreate auto_test_harness start <requests> <amount>\n/thesettlerxcreate auto_test_harness lost_inject | lost_handover_sim | lost_reorder | lost_cancel\n/thesettlerxcreate tracking-reset <colonyId> [scope]\n/thesettlerxcreate diag_output_block, test_output_packaging");
  form.addMultipleChoiceItem().setTitle('Pre-flight complete?').setChoiceValues(['Yes', 'No, and here is what blocked it']).setRequired(true);
  form.addParagraphTextItem().setTitle('Pre-flight notes');

  // Core scenarios, S1 to S6
  form.addPageBreakItem().setTitle('Core lifecycle (S1 to S6)')
    .setHelpText('Request lifecycle from order to terminal state, and the three answers to a lost package.');
  addTest("S1", "Happy path, single order", "Core", "STEPS:\n1. run_live_test once.\n\nEXPECTED: The order to the network is logged, items arrive in the shop rack, a delivery child is created, the courier brings it to the warehouse, and the parent becomes terminal. Nothing stays in IN_PROGRESS.");
  addTest("S2", "Burst path, two orders", "Core", "STEPS:\n1. run_live_test twice in quick succession.\n\nEXPECTED: No over-reservation, no duplicate child for the same parent, both parents terminal.");
  addTest("S3", "Lost package, hand over", "Core", "STEPS:\n1. Steal the package before it reaches the shop (or auto_test_harness lost_inject).\n2. Wait for the overdue question.\n3. Choose 'Hand over package'.\n\nEXPECTED: The in-flight tuple is consumed, a delivery is created from the racks and completes, and the dialog closes without appearing again.");
  addTest("S4", "Lost package, re-order", "Core", "STEPS:\n1. Reach the overdue situation.\n2. Choose 'Start a new order'.\n\nEXPECTED: The old tuple is consumed or replaced, exactly one new order is placed for it, and the request reaches a terminal state.");
  addTest("S5", "Lost package, cancel", "Core", "STEPS:\n1. Reach the overdue situation.\n2. Choose 'Cancel request'.\n\nEXPECTED: Only the intended tuple and root request are cancelled. Unrelated requests for the same item stay active.");
  addTest("S6", "Re-order with too little stock", "Core", "STEPS:\n1. Drain the network below what the request needs.\n2. Choose 'Start a new order'.\n\nEXPECTED: The dialog says the goods are unavailable, and you can go back and recover through hand over or cancel.");

  // Shopkeeper gate, S7 to S12
  form.addPageBreakItem().setTitle('Shopkeeper gate (S7 to S12)')
    .setHelpText('The resolver must not claim requests without a working shopkeeper. Have the shop built and stocked, for example with create:shaft, stripped logs and an axe.');
  addTest("S7", "No shopkeeper, resolver must not claim", "Gate", "STEPS:\n1. Leave the Create Shop without a citizen.\n2. Let a worker (for example the Forester) raise a request for a stocked item.\n3. Wait 5 to 10 seconds.\n\nEXPECTED: The log shows canResolve=false (no shopkeeper working). The request appears on the Clipboard or reaches the right worker. No NullPointerException in sendShopChat.");
  addTest("S8", "Shopkeeper working, resolver claims", "Gate", "STEPS:\n1. Assign a shopkeeper and wait until they are WORKING.\n2. Trigger a request for a stocked item.\n\nEXPECTED: The resolver claims it, a delivery child is created, the courier delivers, the parent goes terminal.");
  addTest("S9", "Shopkeeper unavailable", "Gate", "STEPS:\n1. Put the shopkeeper into a state where they are not working: night sleep, injury, blocked pathing.\n2. Trigger a request.\n\nEXPECTED: The resolver returns false and does not claim. Once the shopkeeper is back, a new request is claimed normally.");
  addTest("S10", "Shopkeeper leaves mid-delivery", "Gate", "STEPS:\n1. Let the resolver claim a request.\n2. Wait until the delivery child is IN_PROGRESS.\n3. Fire or unassign the shopkeeper.\n\nEXPECTED: The delivery window is held, the courier finishes, the parent goes terminal. No stuck request.");
  addTest("S11", "NPE regression, null result stack", "Gate", "STEPS:\n1. Remove the shopkeeper so no deliveries are created and the racks stay empty.\n2. Raise requests for several item kinds including a tool (the Forester's axe).\n3. Let 30 seconds and several cooldown cycles pass.\n\nEXPECTED: No 'NullPointerException: Cannot invoke ItemStack.isEmpty()' anywhere in the log. All requests are on the Clipboard or with other resolvers.");
  addTest("S12", "Shopkeeper rehired", "Gate", "STEPS:\n1. Start without a shopkeeper so requests go elsewhere.\n2. Hire one and wait until WORKING.\n3. Create a new request for a stocked item.\n\nEXPECTED: The new request is claimed. Requests that were rerouted earlier are not processed twice.");

  // Supply rules, S13 to S15
  form.addPageBreakItem().setTitle('Supply rules (S13 to S15)')
    .setHelpText('What the colony may draw from the network, and the window where you set it.');
  addTest("S13", "Block list keeps an item in the network", "Supply", "STEPS:\n1. Put an item the colony wants (for example create:shaft) on the shop's Forbidden list.\n2. Trigger a colony request for it while the network holds plenty.\n\nEXPECTED: The shop does not claim the request; it goes to the Clipboard or another resolver. Items on the Allowed list are still served. Both tabs keep their own icon and survive closing and reopening the hut.");
  addTest("S14", "Network minimum holds stock back", "Supply", "STEPS:\n1. Set a network minimum of 64 for an item while the network holds 80.\n2. Let the colony request 32 of it.\n\nEXPECTED: At most 16 are ordered, the rest waits or goes elsewhere. The shop's own gauge orders respect the same floor.");
  addGridTest("S15", "Amount picker", "Supply", "STEPS:\n1. Type 16.1k into the amount field, confirm, reopen the window.\n2. Guard thirty kinds of item, then press Add.\n3. Open the picker for an item that is already guarded.\n\nEXPECTED: The value reads back as written. At the limit the Add button opens the picker on the already guarded kinds so one can be raised or lowered, instead of doing nothing. The picker pre-fills the current amount of a guarded item.", ["16.1k reads back", "Add button at thirty kinds", "Pre-filled amount"]);

  // Colony Factory Gauge, S16 to S20
  form.addPageBreakItem().setTitle('Colony Factory Gauge (S16 to S20)')
    .setHelpText('Gauge orders, package splitting and where gauge goods wait. This is where most of 0.7.1 changed.');
  addTest("S16", "Two gauges, one waiting on a crafter", "Gauge", "SETUP: Two gauges on the same shop, one asking for an item only a crafter can make.\n\nEXPECTED: The shop serves the gauge whose goods have arrived first; the crafting one does not hold it back. The crafting order reaches the colony's crafters, not the player's request list.");
  addTest("S17", "Partial fulfilment closes the order", "Gauge", "STEPS:\n1. Drain the warehouse below what the gauge asks for.\n2. Let the order run.\n\nEXPECTED: The colony hands over what it has and closes the order. The gauge notices the shortfall and asks again, instead of sitting satisfied with a storage that never reaches its target.");
  addGridTest("S18", "Large order, several packages", "Gauge", "STEPS:\n1. Order more than nine stacks through one gauge.\n2. Save, quit and reload while packages are in transit.\n3. Watch a package that was split over several slots arrive.\n\nEXPECTED: The order travels as several packages and the remainder shows as gauge_order_open. No package vanishes with its chunk. A split package is counted in full, not just its first slot.", ["Splitting and gauge_order_open", "Reload in transit", "Split package counted in full"]);
  addTest("S19", "Gauge goods are not a rack reservation", "Gauge", "STEPS:\n1. With gauge goods waiting in the hut inventory, start a colony pickup and a shop delivery.\n2. Additionally load a world that still has gauge goods lying in its racks.\n\nEXPECTED: Gauge goods are held back from pickup and from the shop's own deliveries, exactly as a rack reservation used to hold them. The old world empties its racks out first.");
  addTest("S20", "Building level gate", "Gauge", "STEPS:\n1. Set gaugeMinBuildingLevel in config/thesettler_x_create-common.toml.\n2. In a second copy, write permaMinBuildingLevel by hand into an old config.\n\nEXPECTED: The old key is carried over to gaugeMinBuildingLevel, and a shop lowered to level 1 stays at level 1 instead of being silently back at 2.");

  // Shop storage, S21 to S24
  form.addPageBreakItem().setTitle('Shop storage (S21 to S24)')
    .setHelpText('Where goods land and who moves them. The racks belong to Create, the hut to the colony.');
  addTest("S21", "Courier goods go to the hut", "Storage", "STEPS:\n1. Empty the racks.\n2. Have a courier bring goods to the shop.\n\nEXPECTED: The goods land in the hut inventory, not the racks. An order from Create can still arrive because the racks stay free.");
  addTest("S22", "Hut full, racks as fallback", "Storage", "STEPS:\n1. Fill the hut inventory.\n2. Have a courier arrive.\n\nEXPECTED: The courier uses the racks, and the shopkeeper carries those goods back into the hut on its next round rather than after the usual five minutes.");
  addTest("S23", "Shop full, courier keeps its goods", "Storage", "STEPS:\n1. Fill hut and racks.\n2. Have a courier arrive.\n3. Repeat with the shop's pickup priority set to zero.\n\nEXPECTED: The courier keeps its goods and says so, and no stack is pulled out of a rack to make room. At priority zero the message says so instead of suggesting more couriers.");
  addGridTest("S24", "Arrival rack stays clear", "Storage", "STEPS:\n1. Let Create unpack into one rack until it is nearly full. The threshold is arrivalRackMinFreeSlots in the config, default 5.\n2. Set the value to 0 and repeat.\n3. Let Create fail to deliver for want of room, then watch the shopkeeper.\n\nEXPECTED: Below the threshold the shopkeeper carries goods over to the other racks, so deliveries keep arriving. At 0 the behaviour is off. After a failed delivery the shopkeeper moves unreserved goods into the hut without waiting five minutes.", ["Carries over below threshold", "Value 0 turns it off", "No five-minute wait after a full shop"]);

  // In-flight pool and ledger, S25 to S27
  form.addPageBreakItem().setTitle('In-flight pool and stock ledger (S25 to S27)')
    .setHelpText('What the shop thinks is on its way, and whether it lets go of it again.');
  addTest("S25", "Many open orders for one item", "Ledger", "STEPS:\n1. Create several colony requests for the same item in quick succession.\n2. Watch the shop's report and the network stock.\n\nEXPECTED: Nothing is ordered twice. The shop keeps at most two notes per item, and a folded note's amount is added to the note it keeps instead of vanishing. What is on its way still adds up to what was ordered.");
  addTest("S26", "In-flight entry with an owner must expire", "Ledger", "STEPS:\n1. Leave an order waiting for goods that never arrive.\n2. Wait well past the usual overdue window.\n\nEXPECTED: The entry expires on its own. If it does not, /thesettlerxcreate tracking-reset <colonyId> inflight is the workaround, and this counts as a Fail.");
  addTest("S27", "Courier dismissed and rehired", "Ledger", "STEPS:\n1. Book a pickup against a courier.\n2. Dismiss them and hire a new one.\n\nEXPECTED: The pickup is found again and completes. No stuck request needing manual surgery.");

  // Reload, migration, server
  form.addPageBreakItem().setTitle('Reload, migration and dedicated server')
    .setHelpText('Everything that has to survive a restart or a second shop.');
  addGridTest("W1", "Save and reload mid-flight", "Reload", "STEPS:\n1. Save and quit while a parent is waiting on in-flight goods.\n2. Save and quit while a delivery child is IN_PROGRESS.\n\nEXPECTED: After loading there are no phantom reorders and no duplicate parents, and the queue recovers without firing and rehiring a courier.", ["Parent waiting on in-flight goods", "Delivery child IN_PROGRESS"]);
  addGridTest("W2", "Load a world written by an older version", "Reload", "STEPS:\n1. Play a world on 0.6.0, then load it with this build.\n2. Look at the shop's stock report, the output block and any gauge orders.\n\nEXPECTED: The stock ledger migrates from NBT without loss, the output block takes its transition path, and no gauge order is lost on load.", ["Ledger migrates", "Output block transition", "Gauge orders survive"]);
  addTest("D1", "Dedicated server", "Server", "STEPS:\n1. Run client and server separately.\n2. Open the shop hut, change supply rules and amounts, and watch a full request run.\n\nEXPECTED: No crash on either side, payloads are authorised, and the hut GUI stays in sync with the server.");
  addTest("D2", "Several shops in one colony", "Server", "STEPS:\n1. Build at least two shops, one with a block list and a network minimum.\n2. Run requests that both could serve.\n\nEXPECTED: Each shop keeps its own rules, ledger and in-flight pool. No request is served twice, and packages go to the right address.");

  // Cleanup safety
  form.addPageBreakItem().setTitle('Cleanup safety');
  addGridTest("C1", "Housekeeping leaves reserved goods alone", "Cleanup", "STEPS:\n1. With an active delivery child, watch what housekeeping does to the reserved pickup items.\n2. After the child is terminal, watch the same goods again.\n\nEXPECTED: While the child is active nothing reserved is moved. Once it is terminal, unreserved cleanup continues as usual.", ["While the child is active", "After the child is terminal"]);

  // Log assertions and overall feedback
  form.addPageBreakItem().setTitle('Log check and overall feedback')
    .setHelpText("Search the whole log, not just the tail. The first two hold whether or not debugLogging is on.\n[CreateShop][problem]  something the mod caught and could not handle\nMC_QUEUE_DEQUEUED_WITHOUT_TERMINAL  a delivery left the courier queue without a terminal state\nThe rest need debugLogging = true:\ninflight arrival  a package arrived in the racks and was assigned to its request\npickup observed  a courier took goods out of the shop\nclaimed=  a request took over goods of a cancelled request\nCreate Shop ordered from network  an order was sent");
  form.addMultipleChoiceItem().setTitle('Did [CreateShop][problem] appear in the log?').setChoiceValues(['No', 'Yes']).setRequired(true);
  form.addParagraphTextItem().setTitle('If yes: paste the lines and say which test they belong to');
  form.addMultipleChoiceItem().setTitle('Did MC_QUEUE_DEQUEUED_WITHOUT_TERMINAL appear?').setChoiceValues(['No', 'Yes']).setRequired(true);
  form.addParagraphTextItem().setTitle('If yes: paste the lines and say which test they belong to');
  form.addMultipleChoiceItem().setTitle('Did the game crash?').setChoiceValues(['No', 'Yes (please link the crash report below)']).setRequired(true);
  form.addMultipleChoiceItem().setTitle('Did you see a double order?').setChoiceValues(['No', 'Yes']).setRequired(true);
  form.addTextItem().setTitle('If yes: in which test?');
  form.addMultipleChoiceItem().setTitle('Did a request get stuck for good?').setChoiceValues(['No', 'Yes']).setRequired(true);
  form.addTextItem().setTitle('If yes: in which test?');
  form.addMultipleChoiceItem().setTitle('Did you need tracking-reset to get a shop moving again?').setChoiceValues(['No', 'Yes']).setRequired(true);
  form.addTextItem().setTitle('If yes: in which test, and which scope?');
  form.addMultipleChoiceItem().setTitle('Did a citizen dialog show a raw number where a name belongs?').setChoiceValues(['No', 'Yes']).setRequired(true);
  form.addParagraphTextItem().setTitle('Top three problems');
  form.addParagraphTextItem().setTitle('Links to logs, crash reports or screenshots').setHelpText('Upload to Google Drive, Discord or a paste site and paste the links here.');
  form.addParagraphTextItem().setTitle('Anything else');

  Logger.log('Edit the form: ' + form.getEditUrl());
  Logger.log('Send to testers: ' + form.getPublishedUrl());
}
