package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import java.util.stream.Collectors;

/** Source helpers for GuardTests that must tell active code apart from commented-out code. */
final class CreateShopGuardSource {

  /**
   * Drops every line whose first non-blank characters are {@code //}. A plain {@code contains}
   * check would otherwise still see a call that was deliberately commented out.
   */
  static String activeCode(String source) {
    return source
        .lines()
        .filter(line -> !line.stripLeading().startsWith("//"))
        .collect(Collectors.joining("\n"));
  }

  private CreateShopGuardSource() {}
}
