package com.thesettler_x_create.create;

import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Identity for one grouped-broadcast bucket: same network+address+requester+request coalesce. */
final class QueuedRequestKey {
  final UUID networkId;
  final String address;
  final String requesterName;
  @Nullable final UUID requestUuid;

  QueuedRequestKey(
      UUID networkId, String address, String requesterName, @Nullable UUID requestUuid) {
    this.networkId = networkId;
    this.address = address == null ? "" : address;
    this.requesterName = requesterName == null ? "" : requesterName;
    this.requestUuid = requestUuid;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof QueuedRequestKey other)) {
      return false;
    }
    return Objects.equals(networkId, other.networkId)
        && Objects.equals(address, other.address)
        && Objects.equals(requesterName, other.requesterName)
        && Objects.equals(requestUuid, other.requestUuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(networkId, address, requesterName, requestUuid);
  }
}
