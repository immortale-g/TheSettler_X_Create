package com.thesettler_x_create.tools;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** CLI tool that edits blueprint NBT fields (schematicName and level). */
public final class BlueprintFixer {
  private BlueprintFixer() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length < 3) {
      err.println("Usage: BlueprintFixer <path> <baseName|-> <level|->");
      return 2;
    }

    Path path = Path.of(args[0]);
    String baseName = args[1];
    String levelArg = args[2];

    String validation = validatePath(path);
    if (validation != null) {
      err.println(validation);
      return 3;
    }
    warnIfNotBlueprint(path, err);

    if (baseName == null || baseName.isEmpty()) {
      err.println("baseName must be non-empty or '-'");
      return 4;
    }
    if (levelArg == null || levelArg.isEmpty()) {
      err.println("level must be an integer or '-'");
      return 4;
    }

    Integer parsedLevel = null;
    if (!"-".equals(levelArg)) {
      try {
        parsedLevel = Integer.parseInt(levelArg);
      } catch (NumberFormatException ex) {
        err.println("level must be an integer or '-' (got: " + levelArg + ")");
        return 4;
      }
    }

    CompoundTag tag;
    try {
      tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    } catch (Exception ex) {
      err.println(
          "Failed to read blueprint NBT: "
              + (ex.getMessage() == null ? "<unknown>" : ex.getMessage()));
      return 5;
    }
    String oldName = tag.getString("schematicName");

    if ("-".equals(baseName)) {
      tag.remove("schematicName");
    } else {
      tag.putString("schematicName", baseName);
    }

    if ("-".equals(levelArg)) {
      tag.remove("level");
    } else {
      tag.putInt("level", parsedLevel.intValue());
    }

    if (tag.contains("name", 8) && oldName.equals(tag.getString("name")) && !"-".equals(baseName)) {
      tag.putString("name", baseName);
    }

    try {
      NbtIo.writeCompressed(tag, path);
    } catch (Exception ex) {
      err.println(
          "Failed to write blueprint NBT: "
              + (ex.getMessage() == null ? "<unknown>" : ex.getMessage()));
      return 6;
    }
    out.println(
        "Fixed blueprint: "
            + path
            + " (schematicName "
            + oldName
            + " -> "
            + baseName
            + ", level="
            + levelArg
            + ")");
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
