package com.thesettler_x_create.tools;

import java.nio.file.Path;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public final class BlueprintTypeFixer {
    private BlueprintTypeFixer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: BlueprintTypeFixer <path> <teId> <buildingType> <blueprintFileName>");
            System.exit(2);
        }

        Path path = Path.of(args[0]);
        String teId = args[1];
        String buildingType = args[2];
        String blueprintFileName = args[3];

        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        ListTag list = root.getList("tile_entities", Tag.TAG_COMPOUND);

        int updated = 0;
        for (Tag t : list) {
            CompoundTag te = (CompoundTag) t;
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
            System.err.println("No tile_entities with id " + teId + " found.");
            System.exit(1);
        }

        NbtIo.writeCompressed(root, path);
        System.out.println("Updated " + updated + " tile_entities in " + path);
    }
}
