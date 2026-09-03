package com.thesettler_x_create.minecolonies.building;

/**
 * NBT tag names shared by {@link ShopLostPackageInteraction} and {@link
 * ShopLostPackageReorderUnavailableInteraction}, which persist the same core lost-package state.
 */
final class LostPackageInteractionTags {
  private LostPackageInteractionTags() {}

  static final String TAG_STACK = "Stack";
  static final String TAG_REMAINING = "Remaining";
  static final String TAG_REQUESTER = "Requester";
  static final String TAG_ADDRESS = "Address";
  static final String TAG_REQUESTED_AT = "RequestedAt";
  static final String TAG_EPOCH = "Epoch";
  static final String TAG_ACTIVE = "Active";
}
