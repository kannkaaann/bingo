package jp.bingo.bingomap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

/**
 * Creates / deletes the temporary, same-seed worlds:
 *   bingo:prep_overworld, bingo:prep_nether   (preparation phase)
 *   bingo:play_overworld, bingo:play_nether   (bingo phase)
 * They copy the main overworld / nether generator and seed, so terrain, structures and
 * chest loot are identical to the main world's pristine state.
 */
public final class BingoWorlds {
    private static final Map<String, List<RuntimeWorldHandle>> OPEN = new HashMap<>();

    private BingoWorlds() {}

    public static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("bingo", path));
    }

    /** Overworld <-> Nether partner of a dimension created here, or null if it is not one of ours. */
    public static ResourceKey<Level> partner(ResourceKey<Level> dim) {
        if (dim == null) return null;
        for (String kind : new String[] {"prep", "play"}) {
            if (dim.equals(key(kind + "_overworld"))) return key(kind + "_nether");
            if (dim.equals(key(kind + "_nether"))) return key(kind + "_overworld");
        }
        return null;
    }

    public static boolean isOurs(ResourceKey<Level> dim) {
        return partner(dim) != null;
    }

    public static synchronized void open(MinecraftServer server, String kind) {
        close(server, kind);
        Fantasy fantasy = Fantasy.get(server);
        ServerLevel overworld = server.overworld();
        long seed = overworld.getSeed();
        List<RuntimeWorldHandle> list = new ArrayList<>();

        RuntimeWorldConfig owCfg = new RuntimeWorldConfig()
            .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
            .setGenerator(overworld.getChunkSource().getGenerator())
            .setSeed(seed)
            .setShouldTickTime(true)
            .setMirrorOverworldGameRules(true);
        list.add(fantasy.openTemporaryWorld(Identifier.fromNamespaceAndPath("bingo", kind + "_overworld"), owCfg));

        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            RuntimeWorldConfig neCfg = new RuntimeWorldConfig()
                .setDimensionType(BuiltinDimensionTypes.NETHER)
                .setGenerator(nether.getChunkSource().getGenerator())
                .setSeed(seed)
                .setShouldTickTime(true)
                .setMirrorOverworldGameRules(true);
            list.add(fantasy.openTemporaryWorld(Identifier.fromNamespaceAndPath("bingo", kind + "_nether"), neCfg));
        }
        OPEN.put(kind, list);
    }

    public static synchronized void close(MinecraftServer server, String kind) {
        List<RuntimeWorldHandle> list = OPEN.remove(kind);
        if (list == null) return;
        for (RuntimeWorldHandle h : list) {
            try {
                h.delete();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
