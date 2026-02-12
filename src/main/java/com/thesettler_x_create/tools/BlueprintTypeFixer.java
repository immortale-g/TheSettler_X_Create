package com.thesettler_x_create.tools;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** CLI tool that updates hut tile entity metadata inside a blueprint. */
public final class BlueprintTypeFixer {
  private BlueprintTypeFixer() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length < 4) {
      err.println("Usage: BlueprintTypeFixer <path> <teId> <buildingType> <blueprintFileName>");
      return 2;
    }

    Path path = Path.of(args[0]);
    String teId = args[1];
    String buildingType = args[2];
    String blueprintFileName = args[3];

    String validation = validatePath(path);
    if (validation != null) {
      err.println(validation);
      return 3;
    }
    warnIfNotBlueprint(path, err);

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

    CompoundTag root;
    try {
      root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    } catch (Exception ex) {
      err.println(
          "Failed to read blueprint NBT: "
              + (ex.getMessage() == null ? "<unknown>" : ex.getMessage()));
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
      te.putString("pack", "");

      if (te.contains("blueprintDataProvider", Tag.TAG_COMPOUND)) {
        CompoundTag bdp = te.getCompound("blueprintDataProvider");
        bdp.putString("path", blueprintFileName);
        bdp.putString("pack", "");
      }

      updated++;
    }

    if (updated == 0) {
      err.println("No tile_entities with id " + teId + " found.");
      return 1;
    }

    try {
      NbtIo.writeCompressed(root, path);
    } catch (Exception ex) {
      err.println(
          "Failed to write blueprint NBT: "
              + (ex.getMessage() == null ? "<unknown>" : ex.getMessage()));
      return 7;
    }
    out.println("Updated " + updated + " tile_entities in " + path);
    return 0;
  }

  private static String validatePath(Path path) {
    if (path == null) {
      return "Path is required.";
    }
    if (!Files.exists(path)) {
      return "Blueprint file does not exist: " + path;
    }
    if (!Files.isRegularFile(path)) {
      return "Blueprint path is not a file: " + path;
    }
    if (!Files.isReadable(path)) {
      return "Blueprint file is not readable: " + path;
    }
    if (!Files.isWritable(path)) {
      return "Blueprint file is not writable: " + path;
    }
    return null;
  }

  private static void warnIfNotBlueprint(Path path, PrintStream err) {
    String name = path.getFileName() == null ? "" : path.getFileName().toString();
    if (!name.endsWith(".blueprint")) {
      err.println("Warning: file does not end with .blueprint (" + name + ")");
    }
  }
}
