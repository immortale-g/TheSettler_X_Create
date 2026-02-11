package com.thesettler_x_create.tools;

import java.nio.file.Path;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

public final class BlueprintFixer {
    private BlueprintFixer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: BlueprintFixer <path> <baseName|-> <level|->");
            System.exit(2);
        }

        Path path = Path.of(args[0]);
        String baseName = args[1];
        String levelArg = args[2];

        CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        String oldName = tag.getString("schematicName");

        if ("-".equals(baseName)) {
            tag.remove("schematicName");
        } else {
            tag.putString("schematicName", baseName);
        }

        if ("-".equals(levelArg)) {
            tag.remove("level");
        } else {
            int level = Integer.parseInt(levelArg);
            tag.putInt("level", level);
        }

        if (tag.contains("name", 8) && oldName.equals(tag.getString("name")) && !"-".equals(baseName)) {
            tag.putString("name", baseName);
        }

        NbtIo.writeCompressed(tag, path);
        System.out.println("Fixed blueprint: " + path + " (schematicName " + oldName + " -> " + baseName + ", level=" + levelArg + ")");
    }
}
