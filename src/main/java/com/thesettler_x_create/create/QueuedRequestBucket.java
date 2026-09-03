package com.thesettler_x_create.create;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** One pending grouped broadcast: the stacks collected so far, and who should send them. */
final class QueuedRequestBucket {
  CreateNetworkFacade facade;
  final List<ItemStack> stacks = new ArrayList<>();

  QueuedRequestBucket(CreateNetworkFacade facade) {
    this.facade = facade;
  }
}
