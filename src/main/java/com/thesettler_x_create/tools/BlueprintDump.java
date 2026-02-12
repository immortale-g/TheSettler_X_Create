package com.thesettler_x_create.tools;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** CLI tool that dumps key metadata fields from a Structurize/MineColonies blueprint NBT. */
public final class BlueprintDump {
  private BlueprintDump() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length < 1) {
      err.println("Usage: BlueprintDump <path>");
      return 2;
    }

    Path path = Path.of(args[0]);
    String validation = validatePath(path);
    if (validation != null) {
      err.println(validation);
      return 3;
    }
    warnIfNotBlueprint(path, err);

    CompoundTag tag;
    try {
      tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    } catch (Exception ex) {
      err.println(
          "Failed to read blueprint NBT: "
              + (ex.getMessage() == null ? "<unknown>" : ex.getMessage()));
      return 4;
    }

    out.println("== Blueprint Dump ==");
    out.println("path=" + path);
    out.println("-- top-level keys --");
    for (String k : tag.getAllKeys()) {
      Tag value = tag.get(k);
      out.println(
          "  " + k + " (" + value.getClass().getSimpleName() + ") = " + value.getAsString());
    }
    dumpKey(out, tag, "schematicName");
    dumpKey(out, tag, "level");
    dumpKey(out, tag, "name");
    dumpKey(out, tag, "path");
    dumpKey(out, tag, "pack");
    dumpKey(out, tag, "mcversion");

    dumpNested(out, tag, "optional_data");
    dumpNested(out, tag, "blueprintDataProvider");
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
    return null;
  }

  private static void warnIfNotBlueprint(Path path, PrintStream err) {
    String name = path.getFileName() == null ? "" : path.getFileName().toString();
    if (!name.endsWith(".blueprint")) {
      err.println("Warning: file does not end with .blueprint (" + name + ")");
    }
  }

  private static void dumpKey(PrintStream out, CompoundTag tag, String key) {
    if (tag.contains(key)) {
      Tag t = tag.get(key);
      out.println(key + " (" + t.getClass().getSimpleName() + ") = " + t.getAsString());
    } else {
      out.println(key + " = <missing>");
    }
  }

  private static void dumpNested(PrintStream out, CompoundTag tag, String key) {
    if (!tag.contains(key, Tag.TAG_COMPOUND)) {
      out.println(key + " = <missing or not compound>");
      return;
    }
    CompoundTag nested = tag.getCompound(key);
    out.println("-- " + key + " keys --");
    for (String k : nested.getAllKeys()) {
      Tag t = nested.get(k);
      out.println("  " + k + " (" + t.getClass().getSimpleName() + ") = " + t.getAsString());
    }
  }
}
