package com.thesettler_x_create.tools;

import java.io.PrintStream;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;

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

    String validation = BlueprintToolSupport.validatePath(path, true);
    if (validation != null) {
      err.println(validation);
      return 3;
    }
    BlueprintToolSupport.warnIfNotBlueprint(path, err);

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

    CompoundTag tag = BlueprintToolSupport.readCompressed(path, err);
    if (tag == null) {
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

    if (!BlueprintToolSupport.writeCompressed(tag, path, err)) {
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
}
