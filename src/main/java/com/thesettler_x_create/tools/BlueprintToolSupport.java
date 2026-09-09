package com.thesettler_x_create.tools;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Shared path-validation and NBT read/write helpers for the blueprint CLI tools. */
final class BlueprintToolSupport {
  private BlueprintToolSupport() {}

  static String validatePath(Path path, boolean requireWritable) {
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
    if (requireWritable && !Files.isWritable(path)) {
      return "Blueprint file is not writable: " + path;
    }
    return null;
  }

  static void warnIfNotBlueprint(Path path, PrintStream err) {
    String name = path.getFileName() == null ? "" : path.getFileName().toString();
    if (!name.endsWith(".blueprint")) {
      err.println("Warning: file does not end with .blueprint (" + name + ")");
    }
  }

  static CompoundTag readCompressed(Path path, PrintStream err) {
    try {
      return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    } catch (Exception ex) {
      err.println(
          "Failed to read blueprint NBT: "
              + (ex.getMessage() == null ? "<unknown>" : ex.getMessage()));
      return null;
    }
  }

  static boolean writeCompressed(CompoundTag tag, Path path, PrintStream err) {
    try {
      NbtIo.writeCompressed(tag, path);
      return true;
    } catch (Exception ex) {
      err.println(
          "Failed to write blueprint NBT: "
              + (ex.getMessage() == null ? "<unknown>" : ex.getMessage()));
      return false;
    }
  }
}
