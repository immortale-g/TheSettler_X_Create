package com.thesettler_x_create.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.foundation.block.IBE;
import com.thesettler_x_create.blockentity.ColonyGaugeBehaviour;
import com.thesettler_x_create.blockentity.ColonyGaugeBlockEntity;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ColonyGaugeBlock extends FaceAttachedHorizontalDirectionalBlock
    implements IBE<ColonyGaugeBlockEntity>, IWrenchable {

  public static final MapCodec<ColonyGaugeBlock> CODEC = simpleCodec(ColonyGaugeBlock::new);
  public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

  public ColonyGaugeBlock(Properties properties) {
    super(properties);
    registerDefaultState(defaultBlockState()
        .setValue(FACE, AttachFace.WALL)
        .setValue(FACING, Direction.NORTH)
        .setValue(POWERED, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder.add(FACE, FACING, POWERED));
  }

  // --- Placement ---

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext pContext) {
    BlockState stateForPlacement = super.getStateForPlacement(pContext);
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
        "[ColonyGaugeBlock] getStateForPlacement clickedPos={} stateNull={} client={}",
        pContext.getClickedPos(), stateForPlacement == null, pContext.getLevel().isClientSide());
    if (stateForPlacement == null) return null;

    Level level = pContext.getLevel();
    BlockPos pos = pContext.getClickedPos();
    BlockState existing = level.getBlockState(pos);
    Vec3 location = pContext.getClickLocation();
    ColonyGaugeBlockEntity be = getBlockEntity(level, pos);

    if (existing.is(this) && location != null && be != null) {
      if (!level.isClientSide()) {
        ItemStack stack = pContext.getItemInHand();
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains("GaugeColonyId")) {
          PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, existing, location);
          int colonyId = data.getInt("GaugeColonyId");
          BlockPos shopPos = BlockPos.of(data.getLong("GaugeShopPos"));
          String dimension = data.getString("GaugeDimension");
          if (be.addPanel(slot, colonyId, shopPos, dimension)) {
            level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
            Player player = pContext.getPlayer();
            if (player != null && !player.isCreative()) {
              stack.shrink(1);
              if (stack.isEmpty()) player.setItemInHand(pContext.getHand(), ItemStack.EMPTY);
            }
          }
        }
      }
    }

    return stateForPlacement;
  }

  // --- Survival & Replacement ---

  @Override
  public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
    return FactoryPanelBlock.canAttachLenient(pLevel, pPos, getConnectedDirection(pState).getOpposite());
  }

  @Override
  public boolean canBeReplaced(BlockState pState, BlockPlaceContext pUseContext) {
    if (!isGaugeStack(pUseContext.getItemInHand())) return false;
    Vec3 location = pUseContext.getClickLocation();
    BlockPos pos = pUseContext.getClickedPos();
    PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, pState, location);
    ColonyGaugeBlockEntity be = getBlockEntity(pUseContext.getLevel(), pos);
    if (be == null) return false;
    return !be.panels.get(slot).isActive();
  }

  // --- Interaction ---

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
      Player player, InteractionHand hand, BlockHitResult hitResult) {
    if (player == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (level.isClientSide) return ItemInteractionResult.SUCCESS;
    if (!isGaugeStack(stack)) return ItemInteractionResult.SUCCESS;

    CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (!data.contains("GaugeColonyId")) {
      player.displayClientMessage(
          Component.literal("Right-click a Create Shop hut first to link the gauge to a colony."), true);
      return ItemInteractionResult.FAIL;
    }

    Vec3 location = hitResult.getLocation();
    if (location == null) return ItemInteractionResult.SUCCESS;

    int colonyId = data.getInt("GaugeColonyId");
    BlockPos shopPos = BlockPos.of(data.getLong("GaugeShopPos"));
    String dimension = data.getString("GaugeDimension");
    PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, state, location);

    withBlockEntityDo(level, pos, be -> {
      if (!be.addPanel(slot, colonyId, shopPos, dimension)) return;
      level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
      if (!player.isCreative()) {
        stack.shrink(1);
        if (stack.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
      }
    });
    return ItemInteractionResult.SUCCESS;
  }

  @Override
  public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
    Level world = context.getLevel();
    BlockPos pos = context.getClickedPos();
    Player player = context.getPlayer();
    if (!(world instanceof ServerLevel)) return InteractionResult.SUCCESS;

    PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, state, context.getClickLocation());
    return onBlockEntityUse(world, pos, be -> {
      ColonyGaugeBehaviour behaviour = be.panels.get(slot);
      if (behaviour == null || !behaviour.isActive()) return InteractionResult.SUCCESS;

      if (!be.removePanel(slot)) return InteractionResult.SUCCESS;

      if (!player.isCreative())
        player.getInventory().placeItemBackInInventory(ModItems.COLONY_GAUGE.get().getDefaultInstance());

      IWrenchable.playRemoveSound(world, pos);
      if (be.activePanels() == 0) world.destroyBlock(pos, false);
      return InteractionResult.SUCCESS;
    });
  }

  @Override
  public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState,
      @Nullable LivingEntity pPlacer, ItemStack pStack) {
    super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
    if (pPlacer == null || pLevel.isClientSide()) return;

    CompoundTag data = pStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (!data.contains("GaugeColonyId")) return;

    int colonyId = data.getInt("GaugeColonyId");
    BlockPos shopPos = BlockPos.of(data.getLong("GaugeShopPos"));
    String dimension = data.getString("GaugeDimension");

    double range = pPlacer.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
    HitResult hitResult = pPlacer.pick(range, 1, false);
    Vec3 location = hitResult.getLocation();
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
        "[ColonyGaugeBlock] setPlacedBy pos={} hitType={} location={}", pPos, hitResult.getType(), location);
    if (location == null) return;

    PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pPos, pState, location);
    withBlockEntityDo(pLevel, pPos, be -> {
      boolean ok = be.addPanel(slot, colonyId, shopPos, dimension);
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[ColonyGaugeBlock] setPlacedBy addPanel slot={} ok={}", slot, ok);
    });
  }

  @Override
  public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
      boolean willHarvest, FluidState fluid) {
    if (tryDestroySubPanelFirst(state, level, pos, player)) return false;
    return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
  }

  private boolean tryDestroySubPanelFirst(BlockState state, Level level, BlockPos pos, Player player) {
    double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
    HitResult hitResult = player.pick(range, 1, false);
    Vec3 location = hitResult.getLocation();
    PanelSlot destroyedSlot = FactoryPanelBlock.getTargetedSlot(pos, state, location);
    return InteractionResult.SUCCESS == onBlockEntityUse(level, pos, be -> {
      if (be.activePanels() < 2) return InteractionResult.FAIL;
      if (!be.removePanel(destroyedSlot)) return InteractionResult.FAIL;
      if (!player.isCreative())
        popResource(level, pos, ModItems.COLONY_GAUGE.get().getDefaultInstance());
      return InteractionResult.SUCCESS;
    });
  }

  // --- Shape ---

  @Override
  public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
    ColonyGaugeBlockEntity be = getBlockEntity(pLevel, pPos);
    if (be != null) {
      VoxelShape shape = be.getShape();
      if (!shape.isEmpty()) return shape;
    }
    return AllShapes.FACTORY_PANEL_FALLBACK.get(FactoryPanelBlock.connectedDirection(pState));
  }

  @Override
  public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
      CollisionContext pContext) {
    if (pContext instanceof EntityCollisionContext ecc && ecc.getEntity() == null)
      return getShape(pState, pLevel, pPos, pContext);
    return Shapes.empty();
  }

  // --- Redstone ---

  @Override
  public boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return state.getValue(POWERED) ? 15 : 0;
  }

  @Override
  public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return state.getValue(POWERED) && FactoryPanelBlock.connectedDirection(state) == direction ? 15 : 0;
  }

  // --- IBE / Block ---

  @Override
  public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
    IBE.onRemove(state, level, pos, newState);
  }

  @Override
  public Class<ColonyGaugeBlockEntity> getBlockEntityClass() {
    return ColonyGaugeBlockEntity.class;
  }

  @Override
  public BlockEntityType<ColonyGaugeBlockEntity> getBlockEntityType() {
    return ModBlockEntities.COLONY_GAUGE.get();
  }

  @Override
  protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
    return CODEC;
  }

  // --- Helper ---

  static boolean isGaugeStack(ItemStack stack) {
    return stack.getItem() instanceof ColonyGaugeBlockItem;
  }
}
