package com.thesettler_x_create.tools;

import java.nio.file.Path;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public final class BlueprintDump {
    private BlueprintDump() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: BlueprintDump <path>");
            System.exit(2);
        }

        Path path = Path.of(args[0]);
        CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());

        System.out.println("== Blueprint Dump ==");
        System.out.println("path=" + path);
        System.out.println("-- top-level keys --");
        for (String k : tag.getAllKeys()) {
            System.out.println("  " + k + " (" + tag.get(k).getClass().getSimpleName() + ") = " + tag.get(k).getAsString());
        }
        dumpKey(tag, "schematicName");
        dumpKey(tag, "level");
        dumpKey(tag, "name");
        dumpKey(tag, "path");
        dumpKey(tag, "pack");
        dumpKey(tag, "mcversion");

        dumpNested(tag, "optional_data");
        dumpNested(tag, "blueprintDataProvider");
    }

    private static void dumpKey(CompoundTag tag, String key) {
        if (tag.contains(key)) {
            Tag t = tag.get(key);
            System.out.println(key + " (" + t.getClass().getSimpleName() + ") = " + t.getAsString());
        } else {
            System.out.println(key + " = <missing>");
        }
    }

    private static void dumpNested(CompoundTag tag, String key) {
        if (!tag.contains(key, 10)) {
            System.out.println(key + " = <missing or not compound>");
            return;
        }
        CompoundTag nested = tag.getCompound(key);
        System.out.println("-- " + key + " keys --");
        for (String k : nested.getAllKeys()) {
            Tag t = nested.get(k);
            System.out.println("  " + k + " (" + t.getClass().getSimpleName() + ") = " + t.getAsString());
        }
    }
}
