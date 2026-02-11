package com.thesettler_x_create.item;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.GlobalLogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public class StockLinkLinkerItem extends Item {
    private static final String FREQ_TAG = "StockLinkFreq";

    public StockLinkLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (context.getPlayer() == null) {
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (context.getPlayer().isShiftKeyDown()) {
            if (!level.isClientSide) {
                stack.remove(DataComponents.CUSTOM_DATA);
                context.getPlayer().displayClientMessage(
                        Component.literal("Stock-Link linker reset."), true);
            }
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(pos);
        LogisticallyLinkedBehaviour behaviour =
                BlockEntityBehaviour.get(level, pos, LogisticallyLinkedBehaviour.TYPE);
        if (behaviour == null && be instanceof StockCheckingBlockEntity stockChecking) {
            behaviour = stockChecking.behaviour;
        }
        if (behaviour == null && be instanceof PackagerLinkBlockEntity packager) {
            behaviour = packager.behaviour;
        }
        if (behaviour == null) {
            if (be instanceof StockTickerBlockEntity) {
                // Consume interaction so the stock ticker UI doesn't open.
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.CONSUME;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (!tag.hasUUID(FREQ_TAG)) {
            tag.putUUID(FREQ_TAG, behaviour.freqId);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            context.getPlayer().displayClientMessage(
                    Component.literal("Stock-Link network stored."), true);
            return InteractionResult.CONSUME;
        }

        UUID newFreq = tag.getUUID(FREQ_TAG);
        UUID oldFreq = behaviour.freqId;
        if (newFreq.equals(oldFreq)) {
            context.getPlayer().displayClientMessage(
                    Component.literal("Stock-Link is already linked to this network."), true);
            return InteractionResult.CONSUME;
        }

        // Remove from old cache, then retune and keep alive in the new network.
        LogisticallyLinkedBehaviour.remove(behaviour);
        behaviour.freqId = newFreq;
        LogisticallyLinkedBehaviour.keepAlive(behaviour);

        if (be != null) {
            be.setChanged();
        }
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);

        GlobalLogisticsManager manager = Create.LOGISTICS;
        if (manager != null) {
            GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
            manager.linkRemoved(oldFreq, globalPos);
            manager.linkInvalidated(oldFreq, globalPos);
            manager.linkLoaded(newFreq, globalPos);
            if (be instanceof PackagerLinkBlockEntity packager) {
                manager.linkAdded(newFreq, globalPos, packager.placedBy);
            }
        }

        if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info("Retuned stock link at {} to {}", pos, newFreq);
        }

        context.getPlayer().displayClientMessage(
                Component.literal("Stock-Link linked to the new network."), true);
        return InteractionResult.CONSUME;
    }

    public static UUID getStoredNetworkId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(FREQ_TAG) ? tag.getUUID(FREQ_TAG) : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                stack.remove(DataComponents.CUSTOM_DATA);
                player.displayClientMessage(
                        Component.literal("Stock-Link linker reset."), true);
            }
            return InteractionResultHolder.consume(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(FREQ_TAG)) {
            tooltip.add(Component.literal("Stored network: " + tag.getUUID(FREQ_TAG)));
            tooltip.add(Component.literal("Right-click: apply to Stock-Link"));
            tooltip.add(Component.literal("Shift + right-click: reset linker"));
        } else {
            tooltip.add(Component.literal("Right-click: store network from Stock-Link"));
            tooltip.add(Component.literal("Shift + right-click: reset linker"));
        }
    }
}
