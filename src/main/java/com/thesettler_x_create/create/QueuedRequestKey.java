package com.thesettler_x_create.create;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Identity for one grouped-broadcast bucket: same network+address+requester+request coalesce. */
record QueuedRequestKey(
    UUID networkId, String address, String requesterName, @Nullable UUID requestUuid) {
  QueuedRequestKey {
    address = address == null ? "" : address;
    requesterName = requesterName == null ? "" : requesterName;
  }
}
