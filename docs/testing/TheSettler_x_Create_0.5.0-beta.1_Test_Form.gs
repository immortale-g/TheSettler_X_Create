/**
 * Creates the "Create Shop 0.5 Test Plan" Google Form in your Google Drive.
 *
 * 1. Open https://script.google.com and click "New project".
 * 2. Replace the code in Code.gs with this whole file and save.
 * 3. Select the function createTestPlanForm and click "Run".
 * 4. Allow access when Google asks (the script only creates this form).
 * 5. Open "Execution log": it prints the edit link and the link to send to testers.
 */
function createTestPlanForm() {
  var form = FormApp.create('Create Shop 0.5 Test Plan');
  form.setDescription("This beta changes how the Create Shop orders, reserves and hands goods to couriers. We need two things from you: test the new flows under load and with interruptions, and confirm that everything that worked before still works. Mod file: thesettler_x_create-0.5.0-beta.1.jar");
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
    .setHelpText("Only test in a copy of your world. The beta saves reservations in a new format. A world that ran 0.5 once loses its reservations when you go back to 0.3.x (no crash, but open requests may order again).\nBack up the world, put the jar into the mods folder and remove the old version.\nIn config/thesettler_x_create-common.toml set debugLogging = true. Without it we can hardly trace a bug.\nFor every problem, save logs/latest.log (on a server the server log) before restarting, and note the time it happened.\nScreenshots of the shop task list, the racks and the chat help more than long descriptions.");
  form.addTextItem().setTitle("Tester (name / Discord)").setHelpText("").setRequired(true);
  form.addTextItem().setTitle("Date").setHelpText("").setRequired(false);
  form.addTextItem().setTitle("Minecraft / NeoForge").setHelpText("1.21.1 / NeoForge 21.1.219 or newer").setRequired(false);
  form.addTextItem().setTitle("MineColonies").setHelpText("1.1.1264 or newer, please also test the latest version").setRequired(false);
  form.addTextItem().setTitle("Structurize").setHelpText("1.0.807 or newer").setRequired(false);
  form.addTextItem().setTitle("Create").setHelpText("6.0.10").setRequired(false);
  form.addMultipleChoiceItem().setTitle("Singleplayer or server").setChoiceValues(['Singleplayer', 'Dedicated server']).setRequired(true);
  form.addTextItem().setTitle("Other mods").setHelpText("").setRequired(false);

  form.addPageBreakItem().setTitle("Upgrading existing worlds").setHelpText("Do these first, while your world still holds state from the old version.");
  addTest("U1", "Load an old world without running requests", "Upgrade", "SETUP: A world played with 0.3.x or 0.4, Create Shop built and linked.\n\nSTEPS:\n1. Load the world with 0.5.0-beta.1.\n2. Look at the shop hut, the pickup block and the racks.\n3. Place a small request (for example 16 iron ingots through a citizen).\n\nEXPECTED: No crash, no error in chat. Shop address and network are still set. The request is delivered normally.");
  addTest("U2", "Load an old world with requests in progress", "Upgrade", "SETUP: With the old version: place a large request (for example 128 ingots) and save while goods are still on their way from the network and a courier already has a delivery.\n\nSTEPS:\n1. Close the world, switch to 0.5.0-beta.1, load the world.\n2. Wait until the request is finished.\n3. Check in the Create network how much was ordered (stock ticker, or stock before and after).\n\nEXPECTED: The request is delivered completely, nothing is ordered twice. After it finishes no goods stay reserved in the racks for good (within about 5 minutes the shopkeeper moves leftovers out).");
  addTest("U3", "Config after the first start", "Upgrade", "STEPS:\n1. Start the game once and quit.\n2. Open config/thesettler_x_create-common.toml.\n\nEXPECTED: The new entry housekeepingMinAgeTicks = 6000 exists, old settings are unchanged.");

  form.addPageBreakItem().setTitle("New flows under load").setHelpText("This is where most changed. Please be mean: cancel, reload, several citizens at once.");
  addTest("H1", "Large request from the Create network", "Hardened", "SETUP: At least 300 iron ingots in the Create network, no iron in the colony warehouse, at least two couriers.\n\nSTEPS:\n1. Trigger a request for 256 iron ingots (builder or citizen request).\n2. Watch the packages arrive in the racks.\n3. Watch when couriers walk to the shop hut.\n\nEXPECTED: Create sends several packages (at most 99 each). As soon as a package is in the racks, deliveries are created for it, even while earlier deliveries are still on their way. Several couriers pick up in parallel and walk to the shop hut. Exactly 256 were taken from the network, once.");
  addTest("H2", "Network has too little, the rest comes later", "Hardened", "SETUP: Only 100 iron ingots in the Create network.\n\nSTEPS:\n1. Trigger a request for 256 iron ingots.\n2. Wait until the 100 are on their way to the citizen.\n3. Put 200 more ingots into the network.\n\nEXPECTED: The 100 are delivered. Once new ingots are in the network, the shop orders the remaining 156 once, at the latest when the first delivery has been picked up. No more than 256 end up at the citizen or in the shop.");
  addTest("H3", "Request accepting mixed item kinds", "Hardened", "SETUP: Several log types in the network, for example 40 oak, 40 birch, 40 spruce.\n\nSTEPS:\n1. Trigger a request that accepts any logs (for example a lumberjack request or a blueprint using a log tag), amount above 64.\n2. Wait for the delivery.\n\nEXPECTED: The shop delivers mixed log types until the amount is reached. No log type stays reserved in the racks after the request is done.");
  addTest("H4", "Cancel while goods are on their way, then request again", "Hardened", "SETUP: A long conveyor route in the Create network, so packages travel for a few seconds.\n\nSTEPS:\n1. Trigger a request for 64 copper ingots.\n2. As soon as the order was sent (chat message), cancel the request (for example in the citizen or colony request window).\n3. Immediately place the same request again, once with the same citizen and once with a different one.\n\nEXPECTED: No second order is sent to the network. The arriving goods go to the new request and are delivered. The log line of the new request shows claimed= with a number above 0.");
  addTest("H5", "Cancel without a new request", "Hardened", "STEPS:\n1. Same as H4, but do not place a new request.\n2. Let the goods arrive and wait 6 minutes.\n\nEXPECTED: The goods first sit unreserved in the racks. After about 5 minutes the shopkeeper visibly carries them into the hut, then a courier takes them to the colony warehouse.");
  addTest("H6", "Cancel while a courier is delivering", "Hardened", "STEPS:\n1. Request 128 ingots, wait until a courier has taken goods out of the shop.\n2. Cancel the request while the courier is on the way.\n3. Let it run for a few minutes.\n\nEXPECTED: No crash, no courier stuck for good. Goods still in the shop are no longer held and are cleared after 5 minutes. No new network order is placed.");
  addTest("H7", "Several requests for the same item at once", "Hardened", "SETUP: 200 iron ingots in the network, three citizens or buildings needing iron.\n\nSTEPS:\n1. Trigger three requests for 64 iron ingots each, shortly after each other.\n2. Wait until all three are done.\n\nEXPECTED: Each gets 64. In total 192 are taken from the network, not more. No request is stuck because another one received “its” goods.");
  addGridTest("H8", "Restart in the middle of a request", "Hardened", "STEPS:\n1. Request 256 ingots. Save and reload the world (restart the server) while the packages are still in the network.\n2. New request. Reload while goods are in the racks and deliveries exist.\n3. New request. Reload while a courier is carrying goods.\n\nEXPECTED: In all three cases the request finishes and nothing is ordered twice. After loading a courier may dump its goods into the warehouse; the shop then creates the delivery again.", ["Goods still in the network", "Goods in racks, deliveries exist", "Courier carrying goods"]);
  addGridTest("H9", "Housekeeping waits 5 minutes", "Hardened", "STEPS:\n1. Put 32 stone into a shop rack by hand (no open request for stone).\n2. Start a timer and watch when the shopkeeper moves them.\n3. Second run: reload the world after 3 minutes and keep timing.\n\nEXPECTED: For 5 minutes the goods stay in the rack. Then the shopkeeper carries them into the hut and a courier picks them up. After the reload it only takes about 2 more minutes, not 5 again.\n\nPLEASE NOTE: Measured time: ____ / ____", ["Without reload", "With reload after 3 minutes"]);
  addTest("H10", "Warehouse pickup never takes reserved goods", "Hardened", "STEPS:\n1. Create a request whose goods sit reserved in the racks while it waits for the rest (for example H2 during the waiting phase).\n2. At the same time create surplus that the shopkeeper carries into the hut (H9).\n3. Watch the courier that picks up the surplus.\n\nEXPECTED: The courier only takes what is in the hut. The reserved goods stay in the racks, and the waiting request is delivered completely later.");
  form.addMultipleChoiceItem().setTitle("H11  Sort button in the shop window  [Hardened]").setHelpText("STEPS:\n1. Fill racks and the hut inventory in a messy way.\n2. Click sort in the shop hut window, if there is one. Otherwise note that it is missing.\n3. Count the amounts before and after.\n\nEXPECTED: Items get sorted, nothing disappears or duplicates, running requests keep being delivered.").setChoiceValues(['Pass', 'Fail', 'No sort button', 'Not tested']).setRequired(true);
  form.addParagraphTextItem().setTitle('H11 notes / log lines').setHelpText('What happened, time of the problem, relevant log lines.');
  addTest("H12", "Tracking reset commands", "Hardened", "SETUP: Operator rights. Look up the colony ID. At least one request is running.\n\nSTEPS:\n1. /thesettlerxcreate tracking-reset <ID> reservations\n2. /thesettlerxcreate tracking-reset <ID> stock-ages\n3. While goods are on their way: /thesettlerxcreate tracking-reset <ID> inflight\n4. /thesettlerxcreate tracking-reset <ID> (everything)\n5. /thesettlerxcreate tracking-reset-all\n6. Wrong scope: /thesettlerxcreate tracking-reset <ID> nonsense\n7. Try it as a player without operator rights.\n\nEXPECTED: Every command reports how much it cleared per scope. inflight adds a note that open requests will order again. Requests are not cancelled and the shop keeps working. A wrong scope lists the allowed values. Without operator rights the command is not available.");
  addTest("H13", "Sustained load with the builder", "Hardened", "SETUP: A builder with a large build (many different materials), materials almost only in the Create network, three or more couriers.\n\nSTEPS:\n1. Start the build and let it run for at least 30 minutes.\n2. Reload the world once in between.\n3. Watch server TPS (for example with Spark).\n\nEXPECTED: The build progresses, no request is stuck for minutes without reason, no noticeable surplus piles up in the warehouse. TPS stays stable.\n\nPLEASE NOTE: TPS before / during: ____ / ____");
  addTest("H14", "A package never arrives", "Hardened", "STEPS:\n1. Trigger a request, then intercept the package on its way (break the conveyor, pick the package up).\n2. Wait 6 minutes.\n3. Answer the shopkeeper's question once with reorder, once with cancel and once by handing the package over.\n4. Additionally: cancel the request first, then intercept the package.\n\nEXPECTED: For a running request the shopkeeper asks after about 5 minutes, and each answer does what it says. For a cancelled request there is no question; the entry expires silently.");

  form.addPageBreakItem().setTitle("Existing features").setHelpText("These must behave exactly as in 0.3.4.");
  addTest("R1", "Small standard request", "Regression", "STEPS:\n1. Request one stack of an item that is only in the Create network.\n\nEXPECTED: Chat message about the order, the package arrives, a courier delivers, the request is done.");
  addTest("R2", "The warehouse has the goods itself", "Regression", "STEPS:\n1. Put 32 iron ingots into the colony warehouse and some into the network.\n2. Request 16 iron ingots.\n\nEXPECTED: The warehouse delivers. The shop orders nothing from the network.");
  addTest("R3", "Colony Factory Gauge", "Regression", "SETUP: Shop at level 2, Colony Gauge on a Colony Packager with a chest, goods in the colony warehouse.\n\nSTEPS:\n1. Set filter and target amount (for example 10) on the gauge, set the address.\n2. Wait for the delivery, then take 5 items out of the chest.\n3. Set the shop to level 1 for testing and trigger the gauge again.\n\nEXPECTED: The warehouse delivers to the shop, the output block packages, the package arrives at the gauge address. Only 5 are requested again. At level 1 nothing is requested. The request is visible in the shop task list.");
  addTest("R4", "Hand a lost package over manually", "Regression", "STEPS:\n1. Pick up the package from H14.\n2. Hand it to the shopkeeper when they ask about the lost package.\n\nEXPECTED: The contents go into the racks, the request is served with them and not ordered again.");
  addTest("R5", "A player takes reserved goods out of a rack", "Regression", "STEPS:\n1. Request 64 ingots, wait until they are in the racks and a delivery exists.\n2. Before the courier arrives, take the ingots out of the rack by hand.\n\nEXPECTED: The delivery fails, the request is reassigned and the missing amount is ordered again. Nothing stays stuck.");
  addTest("R6", "Racks full", "Regression", "STEPS:\n1. Fill the shop racks almost completely.\n2. Trigger a large request for a new item.\n\nEXPECTED: The shop only orders what fits into the racks, and the shopkeeper reports the lack of space. No packages get stuck at the packager.");
  addTest("R7", "Builder places Create blocks", "Regression", "STEPS:\n1. Build a blueprint that contains Create blocks (shafts, cogwheels, belts, packagers).\n\nEXPECTED: Blocks are placed with the right orientation, materials are requested correctly, nothing is used twice.");
  addTest("R8", "Task list and chat messages", "Regression", "STEPS:\n1. Open the shop hut task list during H1.\n2. Follow the chat during a request.\n\nEXPECTED: Running requests and deliveries are shown. Chat messages appear, but no spam.");
  addTest("R9", "Couriers still work for the colony", "Regression", "STEPS:\n1. Let normal colony deliveries run (warehouse to buildings, pickups) while the shop is busy.\n2. Check the courier assignment in the warehouse window.\n\nEXPECTED: Couriers also do non-shop tasks, nobody stands still for good.");
  addTest("R10", "Maintenance commands (test world only)", "Regression", "STEPS:\n1. With running requests run /thesettlerxcreate reset_live_state.\n2. In another copy run /thesettlerxcreate prepare_uninstall.\n\nEXPECTED: reset_live_state cancels shop requests and prints a summary, then the shop accepts new requests. prepare_uninstall reports success and the next step.");
  addTest("R11", "Server with several players and several shops", "Regression", "SETUP: Dedicated server, two players, one colony with two Create Shops on different addresses.\n\nSTEPS:\n1. Repeat H1 and H7 on the server.\n2. Both players open shop windows at the same time.\n\nEXPECTED: No crash on server or client, packages go to the right shop, requests are not served twice by both shops.");

  form.addPageBreakItem().setTitle('Log keywords and overall feedback')
    .setHelpText("Only with debugLogging = true. If you find these around the time of a problem, paste the lines into the notes:\ninflight arrival: a package arrived in the racks and was assigned to its request\npickup observed: a courier took goods out of the shop\nclaimed=: a request took over goods of a cancelled request\nopen deliveries: a request with deliveries in progress is being served\ninflight dropped unowned: goods without a request never arrived and were dropped\ntracking reset: a reset command ran");
  form.addMultipleChoiceItem().setTitle('Did the game crash?').setChoiceValues(['No', 'Yes (please attach or link the crash report below)']).setRequired(true);
  form.addMultipleChoiceItem().setTitle('Did you see a double order?').setChoiceValues(['No', 'Yes']).setRequired(true);
  form.addTextItem().setTitle('If yes: in which test?');
  form.addMultipleChoiceItem().setTitle('Did a request get stuck for good?').setChoiceValues(['No', 'Yes']).setRequired(true);
  form.addTextItem().setTitle('If yes: in which test?');
  form.addMultipleChoiceItem().setTitle('Large requests compared to 0.3.4').setChoiceValues(['Faster', 'Same', 'Slower', 'Did not compare']);
  form.addParagraphTextItem().setTitle('Top three problems');
  form.addParagraphTextItem().setTitle('Links to logs, crash reports or screenshots').setHelpText('Upload to Google Drive, Discord or a paste site and paste the links here.');
  form.addParagraphTextItem().setTitle('Anything else');

  Logger.log('Edit the form: ' + form.getEditUrl());
  Logger.log('Send to testers: ' + form.getPublishedUrl());
}
