package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.building.ShopTrackingResetReport;
import com.thesettler_x_create.minecolonies.building.ShopTrackingScope;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Operator commands that reset the tracking Create Shops keep on their own, modelled on
 * MineColonies' {@code requestsystem-reset} and {@code requestsystem-reset-all}:
 *
 * <ul>
 *   <li>{@code tracking-reset <colonyId> [scope]}
 *   <li>{@code tracking-reset-all [scope]}
 * </ul>
 *
 * <p>The scope defaults to {@code all}. Requests are not cancelled; {@code reset_live_state} does
 * that.
 */
final class CreateShopTrackingResetCommands {
  private static final String COLONY_ID = "colonyId";
  private static final String SCOPE = "scope";

  private CreateShopTrackingResetCommands() {}

  static LiteralArgumentBuilder<CommandSourceStack> resetColony() {
    return Commands.literal("tracking-reset")
        .then(
            Commands.argument(COLONY_ID, IntegerArgumentType.integer(1))
                .executes(context -> runForColony(context, ShopTrackingScope.ALL))
                .then(
                    Commands.argument(SCOPE, StringArgumentType.word())
                        .suggests(
                            (context, builder) ->
                                SharedSuggestionProvider.suggest(scopeSuggestions(), builder))
                        .executes(
                            context ->
                                runForColony(
                                    context, StringArgumentType.getString(context, SCOPE)))));
  }

  static LiteralArgumentBuilder<CommandSourceStack> resetAllColonies() {
    return Commands.literal("tracking-reset-all")
        .executes(context -> runForAllColonies(context, ShopTrackingScope.ALL))
        .then(
            Commands.argument(SCOPE, StringArgumentType.word())
                .suggests(
                    (context, builder) ->
                        SharedSuggestionProvider.suggest(scopeSuggestions(), builder))
                .executes(
                    context ->
                        runForAllColonies(context, StringArgumentType.getString(context, SCOPE))));
  }

  private static int runForColony(
      CommandContext<CommandSourceStack> context, String scopeArgument) {
    Optional<Set<ShopTrackingScope>> scopes = parseOrFail(context.getSource(), scopeArgument);
    if (scopes.isEmpty()) {
      return 0;
    }
    int colonyId = IntegerArgumentType.getInteger(context, COLONY_ID);
    IColony colony =
        IColonyManager.getInstance().getColonyByWorld(colonyId, context.getSource().getLevel());
    if (colony == null) {
      context
          .getSource()
          .sendFailure(
              Component.literal(
                  "[CreateShop] No colony with id "
                      + colonyId
                      + " in this dimension. Run the command from the colony's dimension."));
      return 0;
    }
    return report(context.getSource(), resetColony(colony, scopes.get()));
  }

  private static int runForAllColonies(
      CommandContext<CommandSourceStack> context, String scopeArgument) {
    Optional<Set<ShopTrackingScope>> scopes = parseOrFail(context.getSource(), scopeArgument);
    if (scopes.isEmpty()) {
      return 0;
    }
    ShopTrackingResetReport total = new ShopTrackingResetReport();
    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      total.addAll(resetColony(colony, scopes.get()));
    }
    return report(context.getSource(), total);
  }

  private static ShopTrackingResetReport resetColony(
      IColony colony, Set<ShopTrackingScope> scopes) {
    ShopTrackingResetReport report = new ShopTrackingResetReport();
    for (BuildingCreateShop shop : CreateShopCommandSupport.collectCreateShops(colony)) {
      report.addAll(shop.resetTracking(scopes));
    }
    return report;
  }

  private static Optional<Set<ShopTrackingScope>> parseOrFail(
      CommandSourceStack source, String scopeArgument) {
    Optional<Set<ShopTrackingScope>> scopes = ShopTrackingScope.parse(scopeArgument);
    if (scopes.isEmpty()) {
      source.sendFailure(
          Component.literal(
              "[CreateShop] Unknown scope '"
                  + scopeArgument
                  + "'. Use one of: "
                  + String.join(", ", scopeSuggestions())));
    }
    return scopes;
  }

  private static int report(CommandSourceStack source, ShopTrackingResetReport report) {
    source.sendSuccess(
        () -> Component.literal("[CreateShop] Tracking reset: " + report.summary()), true);
    if (report.forgottenInflight() > 0) {
      source.sendSuccess(
          () ->
              Component.literal(
                  "[CreateShop] "
                      + report.forgottenInflight()
                      + " order(s) were still on their way. Open requests order again; goods that"
                      + " still arrive stay in the racks unreserved."),
          true);
    }
    return report.shops() > 0 ? 1 : 0;
  }

  static List<String> scopeSuggestions() {
    List<String> suggestions = new java.util.ArrayList<>();
    suggestions.add(ShopTrackingScope.ALL);
    for (ShopTrackingScope scope : ShopTrackingScope.values()) {
      suggestions.add(scope.id());
    }
    return suggestions;
  }
}
