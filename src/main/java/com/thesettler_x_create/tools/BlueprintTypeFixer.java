package com.thesettler_x_create.tools;

import java.io.PrintStream;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** CLI tool that updates hut tile entity metadata inside a blueprint. */
public final class BlueprintTypeFixer {
  private BlueprintTypeFixer() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length < 4) {
      err.println(
          "Usage: BlueprintTypeFixer <path> <teId> <buildingType> <blueprintFileName> [pack]");
      return 2;
    }

    Path path = Path.of(args[0]);
    String teId = args[1];
    String buildingType = args[2];
    String blueprintFileName = args[3];
    String pack = args.length >= 5 ? args[4] : "";

    String validation = BlueprintToolSupport.validatePath(path, true);
    if (validation != null) {
      err.println(validation);
      return 3;
    }
    BlueprintToolSupport.warnIfNotBlueprint(path, err);

    if (teId == null || teId.isBlank()) {
      err.println("teId must be non-empty.");
      return 4;
    }
    if (buildingType == null || buildingType.isBlank()) {
      err.println("buildingType must be non-empty.");
      return 4;
    }
    if (blueprintFileName == null || blueprintFileName.isBlank()) {
      err.println("blueprintFileName must be non-empty.");
      return 4;
    }

    CompoundTag root = BlueprintToolSupport.readCompressed(path, err);
    if (root == null) {
      return 5;
    }
    if (!root.contains("tile_entities", Tag.TAG_LIST)) {
      err.println("Blueprint is missing tile_entities list.");
      return 6;
    }

    ListTag list = root.getList("tile_entities", Tag.TAG_COMPOUND);
    if (list == null || list.isEmpty()) {
      err.println("Blueprint tile_entities list is empty.");
      return 6;
    }

    int updated = 0;
    for (Tag t : list) {
      if (!(t instanceof CompoundTag te)) {
        continue;
      }
      if (!teId.equals(te.getString("id"))) {
        continue;
      }

      te.putString("type", buildingType);
      te.putString("path", blueprintFileName);
      te.putString("pack", pack);

      if (te.contains("blueprintDataProvider", Tag.TAG_COMPOUND)) {
        CompoundTag bdp = te.getCompound("blueprintDataProvider");
        bdp.putString("path", blueprintFileName);
        bdp.putString("pack", pack);
      }

      updated++;
    }

    if (updated == 0) {
      err.println("No tile_entities with id " + teId + " found.");
      return 1;
    }

    if (!BlueprintToolSupport.writeCompressed(root, path, err)) {
      return 7;
    }
    out.println("Updated " + updated + " tile_entities in " + path);
    return 0;
  }
}
