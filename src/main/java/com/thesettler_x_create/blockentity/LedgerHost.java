package com.thesettler_x_create.blockentity;

/**
 * What the ledgers of a {@link CreateShopBlockEntity} need from their block entity: the server
 * thread check, the game time and a way to mark the chunk dirty. Keeps the ledgers from depending
 * on the whole block entity.
 */
interface LedgerHost {

  /**
   * True when called on the server thread with a loaded level; logs and returns false otherwise.
   */
  boolean ensureServerThread(String action);

  /** Current game time, or 0 without a level. */
  long gameTime();

  boolean hasLevel();

  /** Marks the block entity as changed so its state is saved. */
  void markChanged();
}
