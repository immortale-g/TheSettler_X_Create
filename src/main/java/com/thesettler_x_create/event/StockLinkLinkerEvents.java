package com.thesettler_x_create.event;

import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.thesettler_x_create.item.StockLinkLinkerItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class StockLinkLinkerEvents {
  private StockLinkLinkerEvents() {}

  @SubscribeEvent
  public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    ItemStack stack = event.getItemStack();
    if (!(stack.getItem() instanceof StockLinkLinkerItem)) {
      return;
    }

    Level level = event.getLevel();
    BlockEntity be = level.getBlockEntity(event.getPos());
    if (!(be instanceof StockTickerBlockEntity) && !(be instanceof StockCheckingBlockEntity)) {
      return;
    }

    // Delegate to the item's logic and consume the click to prevent the Stock Ticker UI.
    BlockHitResult hit = event.getHitVec();
    InteractionResult result =
        stack
            .getItem()
            .useOn(
                new net.minecraft.world.item.context.UseOnContext(
                    event.getEntity(), event.getHand(), hit));

    if (result.consumesAction()) {
      event.setCanceled(true);
      event.setCancellationResult(result);
    }
  }
}
