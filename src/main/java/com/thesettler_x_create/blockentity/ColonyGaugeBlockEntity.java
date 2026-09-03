package com.thesettler_x_create.blockentity;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.thesettler_x_create.block.ColonyGaugeBlock;
import com.thesettler_x_create.init.ModBlockEntities;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ColonyGaugeBlockEntity extends SmartBlockEntity {

  public EnumMap<PanelSlot, ColonyGaugeBehaviour> panels;
  public boolean redraw;
  public VoxelShape lastShape;

  public ColonyGaugeBlockEntity(BlockPos pos, BlockState state) {
    this(ModBlockEntities.COLONY_GAUGE.get(), pos, state);
  }

  public ColonyGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
    setLazyTickRate(20);
  }

  @Override
  public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    panels = new EnumMap<>(PanelSlot.class);
    redraw = true;
    for (PanelSlot slot : PanelSlot.values()) {
      ColonyGaugeBehaviour b = new ColonyGaugeBehaviour(this, slot);
      panels.put(slot, b);
      behaviours.add(b);
    }
  }

  @Override
  protected AABB createRenderBoundingBox() {
    return new AABB(worldPosition).inflate(8);
  }

  @Override
  public void lazyTick() {
    super.lazyTick();
    if (level == null || level.isClientSide()) return;

    if (activePanels() == 0) {
      level.setBlockAndUpdate(worldPosition, Blocks.AIR.defaultBlockState());
      return;
    }

    scanFrogports();
  }

  /**
   * Returns the Packager this gauge is mounted on, or {@code null} if it isn't attached to one.
   * Mirrors Create's own restocker-mode gauges, which read the actual current stock of the
   * Packager's connected inventory (chest/vault/etc.) rather than tracking deliveries by count — so
   * the gauge stays in sync even if items are later removed from that inventory.
   */
  @org.jetbrains.annotations.Nullable
  public com.simibubi.create.content.logistics.packager.PackagerBlockEntity getConnectedPackager() {
    if (level == null) return null;
    Direction connectedDir = FactoryPanelBlock.connectedDirection(getBlockState());
    BlockPos attachedPos = worldPosition.relative(connectedDir.getOpposite());
    if (level.getBlockEntity(attachedPos)
        instanceof com.simibubi.create.content.logistics.packager.PackagerBlockEntity packager) {
      return packager;
    }
    return null;
  }

  /** Scans for adjacent/above-packager Frogports and updates each active slot's address. */
  private void scanFrogports() {
    BlockState state = getBlockState();
    Direction connectedDir = FactoryPanelBlock.connectedDirection(state);
    // connectedDir is the panel's "front" direction (away from attached block)
    BlockPos attachedPos = worldPosition.relative(connectedDir.getOpposite());
    boolean attachedIsPackager =
        level.getBlockEntity(attachedPos)
            instanceof com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

    for (ColonyGaugeBehaviour behaviour : panels.values()) {
      if (!behaviour.isActive()) continue;
      String found = null;
      if (attachedIsPackager) {
        BlockPos abovePackager = attachedPos.above();
        if (level.getBlockEntity(abovePackager) instanceof PackagePortBlockEntity port) {
          String addr = port.addressFilter;
          if (addr != null && !addr.isBlank()) found = addr;
        }
      } else {
        for (Direction dir : Direction.values()) {
          BlockPos neighbor = worldPosition.relative(dir);
          if (level.getBlockEntity(neighbor) instanceof PackagePortBlockEntity port) {
            String addr = port.addressFilter;
            if (addr != null && !addr.isBlank()) {
              found = addr;
              break;
            }
          }
        }
      }
      if (!Objects.equals(found, behaviour.cachedFrogportAddress)) {
        behaviour.cachedFrogportAddress = found;
        sendData();
      }
    }
  }

  public boolean addPanel(PanelSlot slot, int colonyId, BlockPos shopPos, String dimension) {
    ColonyGaugeBehaviour behaviour = panels.get(slot);
    if (behaviour != null && !behaviour.isActive()) {
      behaviour.enable(colonyId, shopPos, dimension);
      redraw = true;
      lastShape = null;
      return true;
    }
    return false;
  }

  public boolean removePanel(PanelSlot slot) {
    ColonyGaugeBehaviour behaviour = panels.get(slot);
    if (behaviour != null && behaviour.isActive()) {
      behaviour.disable();
      redraw = true;
      lastShape = null;
      return true;
    }
    return false;
  }

  public int activePanels() {
    int result = 0;
    for (ColonyGaugeBehaviour b : panels.values()) if (b.isActive()) result++;
    return result;
  }

  /**
   * Called by each ColonyGaugeBehaviour when its satisfied/promisedSatisfied state changes.
   *
   * <p>Deliberately never sets {@code POWERED} to {@code true}: unlike our block model (which maps
   * both powered states to the identical model, so there is no visual to gain), setting it makes
   * {@link ColonyGaugeBlock#getDirectSignal} emit a real, sustained redstone signal toward the
   * connected Packager — which then continuously re-triggers its (Create-native) redstone-mode
   * sending logic, vacuuming whatever sits in its target inventory. Real Create's FactoryPanelBlock
   * has the identical getSignal/getDirectSignal code, but never actually sets POWERED either — it's
   * unused there too. Kept as a no-op (rather than removing the block state) in case a powered
   * model variant is ever wired up.
   */
  public void updatePowered() {
    if (level == null) return;
    BlockState state = getBlockState();
    if (!state.hasProperty(ColonyGaugeBlock.POWERED)) return;
    if (!state.getValue(ColonyGaugeBlock.POWERED)) return;
    level.setBlock(
        worldPosition, state.setValue(ColonyGaugeBlock.POWERED, false), Block.UPDATE_ALL);
  }

  /**
   * Called by ColonyPackagerBlockEntity after unwrapping a box. Tries to match by item; falls back
   * to first promisedSatisfied slot.
   */
  public void onDeliveryReceived(ItemStack deliveredItem) {
    for (ColonyGaugeBehaviour behaviour : panels.values()) {
      if (!behaviour.isActive() || !behaviour.promisedSatisfied) continue;
      if (ItemStack.isSameItem(behaviour.getFilter(), deliveredItem)) {
        behaviour.onDeliveryReceived();
        return;
      }
    }
    // Fallback: no item match — mark first waiting slot as done
    onDeliveryReceived();
  }

  public void onDeliveryReceived() {
    for (ColonyGaugeBehaviour behaviour : panels.values()) {
      if (behaviour.isActive() && behaviour.promisedSatisfied) {
        behaviour.onDeliveryReceived();
        return;
      }
    }
  }

  @Override
  public void destroy() {
    super.destroy();
    int count = activePanels();
    if (count > 1)
      Block.popResource(
          level,
          worldPosition,
          com.thesettler_x_create.init.ModItems.COLONY_GAUGE
              .get()
              .getDefaultInstance()
              .copyWithCount(count - 1));
  }

  public VoxelShape getShape() {
    if (lastShape != null) return lastShape;

    BlockState state = getBlockState();
    float xRot = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state) + 90;
    float yRot = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state);
    Direction connectedDirection = FactoryPanelBlock.connectedDirection(state);
    Vec3 inflateAxes = VecHelper.axisAlingedPlaneOf(connectedDirection);

    lastShape = Shapes.empty();
    for (ColonyGaugeBehaviour behaviour : panels.values()) {
      if (!behaviour.isActive()) continue;
      PanelSlot slot = behaviour.slot;
      Vec3 vec = new Vec3(.25 + slot.xOffset * .5, 1 / 16f, .25 + slot.yOffset * .5);
      vec = VecHelper.rotateCentered(vec, 180, Axis.Y);
      vec = VecHelper.rotateCentered(vec, xRot, Axis.X);
      vec = VecHelper.rotateCentered(vec, yRot, Axis.Y);
      AABB bb =
          new AABB(vec, vec)
              .inflate(1 / 16f)
              .inflate(inflateAxes.x * 3 / 16f, inflateAxes.y * 3 / 16f, inflateAxes.z * 3 / 16f);
      lastShape = Shapes.or(lastShape, Shapes.create(bb));
    }
    return lastShape;
  }
}
