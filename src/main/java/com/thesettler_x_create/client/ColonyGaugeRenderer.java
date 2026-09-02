package com.thesettler_x_create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import com.thesettler_x_create.blockentity.ColonyGaugeBehaviour;
import com.thesettler_x_create.blockentity.ColonyGaugeBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class ColonyGaugeRenderer extends SmartBlockEntityRenderer<ColonyGaugeBlockEntity> {

  public ColonyGaugeRenderer(Context context) {
    super(context);
  }

  @Override
  protected void renderSafe(ColonyGaugeBlockEntity be, float partialTicks, PoseStack ms,
      MultiBufferSource buffer, int light, int overlay) {
    super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    BlockState blockState = be.getBlockState();
    float xRot = FactoryPanelBlock.getXRot(blockState) + Mth.PI / 2;
    float yRot = FactoryPanelBlock.getYRot(blockState);

    for (ColonyGaugeBehaviour behaviour : be.panels.values()) {
      if (!behaviour.isActive()) continue;

      boolean lit = behaviour.satisfied || behaviour.promisedSatisfied;
      PartialModel panelModel = lit ? ModPartialModels.COLONY_GAUGE_PANEL_WITH_BULB : ModPartialModels.COLONY_GAUGE_PANEL;

      CachedBuffers.partial(panelModel, blockState)
          .rotateCentered(yRot, Direction.UP)
          .rotateCentered(xRot, Direction.EAST)
          .rotateCentered(Mth.PI, Direction.UP)
          .translate(behaviour.slot.xOffset * .5, 0, behaviour.slot.yOffset * .5)
          .light(light)
          .overlay(overlay)
          .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

      float glow = behaviour.bulb.getValue(partialTicks);
      if (glow > 0.05f) {
        // Green for satisfied, amber for promisedSatisfied
        int r = behaviour.satisfied ? 40 : 255;
        int g = behaviour.satisfied ? 200 : 165;
        int b = behaviour.satisfied ? 80 : 0;

        CachedBuffers.partial(ModPartialModels.COLONY_GAUGE_BULB_LIGHT, blockState)
            .rotateCentered(yRot, Direction.UP)
            .rotateCentered(xRot, Direction.EAST)
            .rotateCentered(Mth.PI, Direction.UP)
            .translate(behaviour.slot.xOffset * .5, 0, behaviour.slot.yOffset * .5)
            .light(LightTexture.FULL_BRIGHT)
            .color(r, g, b, 255)
            .overlay(overlay)
            .renderInto(ms, buffer.getBuffer(RenderTypes.additive()));
      }
    }
  }
}
