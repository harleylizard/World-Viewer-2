package dev.corgitaco.worldviewer.common.storage;


import dev.corgitaco.worldviewer.client.tile.TileLayerRenderTileManager;
import dev.corgitaco.worldviewer.common.WorldViewer;
import dev.corgitaco.worldviewer.mixin.IOWorkerAccessor;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.daporkchop.lib.primitive.map.concurrent.LongObjConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DataTileManager {

    private static final ExecutorService SAVE_THREAD = TileLayerRenderTileManager.createExecutor(1, "Worker-Data-Tile-Saver-IO");

    private final LongObjConcurrentHashMap<DataTile> dataTiles = new LongObjConcurrentHashMap<>();
    private final Path saveDir;
    private final ChunkGenerator generator;
    private final BiomeSource source;
    private final ServerLevel serverLevel;

    private static IOWorker ioWorker;

    private long worldSeed;


    public DataTileManager(Path saveDir, ChunkGenerator generator, BiomeSource source, ServerLevel serverLevel, long worldSeed) {
        this.saveDir = saveDir;
        this.generator = generator;
        this.source = source;
        this.serverLevel = serverLevel;
        this.worldSeed = worldSeed;
        File saveDirAsFile = saveDir.toFile();

        if (!saveDirAsFile.exists()) {
            try {
                Files.createDirectories(saveDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (!saveDirAsFile.isDirectory()) {
            throw new IllegalArgumentException("Path must be a directory");
        }

        if (ioWorker == null) {
            ioWorker = IOWorkerAccessor.makeStorage(saveDir, true, "data_tiles");
        }
    }

    public ServerLevel serverLevel() {
        return serverLevel;
    }

    public int getHeightRaw(Heightmap.Types types, int blockX, int blockZ) {
        return this.generator.getBaseHeight(blockX, blockZ, types, this.serverLevel, this.serverLevel.getChunkSource().randomState());
    }

    public Holder<Biome> getBiomeRaw(int worldX, int worldZ) {
        return this.source.getNoiseBiome(QuartPos.fromBlock(worldX), QuartPos.fromBlock(63), QuartPos.fromBlock(worldZ), this.serverLevel.getChunkSource().randomState().sampler());
    }

    public boolean isSlimeChunkRaw(int chunkX, int chunkZ) {
        return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, this.worldSeed, 987234911L).nextInt(10) == 0;
    }

    public boolean isSlimeChunk(int chunkX, int chunkZ) {
        return getTile(chunkX, chunkZ).isSlimeChunk();
    }

    public int getHeight(Heightmap.Types type, int blockX, int blockZ) {
        return getTileFromWorldCoords(blockX, blockZ).getHeight(type, blockX, blockZ);
    }

    public Holder<Biome> getBiome(int blockX, int blockZ) {
        return getTileFromWorldCoords(blockX, blockZ).getBiome(blockX, blockZ);
    }

    public Set<Holder<Structure>> getStructures(int chunkX, int chunkZ) {
        return getTile(chunkX, chunkZ).structures();
    }


    public Set<Holder<Structure>> getStructuresRaw(long chunkKey) {

        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);

        ObjectOpenHashSet<Holder<Structure>> structures = new ObjectOpenHashSet<>();

        RegistryAccess registryAccess = serverLevel.registryAccess();
        for (Holder<StructureSet> structureSetHolder : registryAccess.registryOrThrow(Registries.STRUCTURE_SET).asHolderIdMap()) {

            StructureSet structureSet = structureSetHolder.value();

            if (structureSet.placement().isStructureChunk(this.serverLevel.getChunkSource().chunkMap.generatorState(), chunkX, chunkZ)) {
                WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
                worldgenrandom.setLargeFeatureSeed(serverLevel.getSeed(), chunkX, chunkZ);
                List<StructureSet.StructureSelectionEntry> arraylist = new ArrayList<>(structureSet.structures());

                int i = 0;

                for (StructureSet.StructureSelectionEntry structureset$structureselectionentry1 : arraylist) {
                    i += structureset$structureselectionentry1.weight();
                }

                while (!arraylist.isEmpty()) {
                    int j = worldgenrandom.nextInt(i);
                    int k = 0;

                    for (StructureSet.StructureSelectionEntry structureset$structureselectionentry2 : arraylist) {
                        j -= structureset$structureselectionentry2.weight();
                        if (j < 0) {
                            break;
                        }

                        ++k;
                    }

                    StructureSet.StructureSelectionEntry structureset$structureselectionentry3 = arraylist.get(k);
                    Holder<Structure> configuredStructureFeatureHolder = structureset$structureselectionentry3.structure();
                    Structure value = configuredStructureFeatureHolder.value();
                    if (canCreate(serverLevel, generator, chunkX, chunkZ, value)) {
                        structures.add(configuredStructureFeatureHolder);
                        break;
                    }

                    arraylist.remove(k);
                    i -= structureset$structureselectionentry3.weight();
                }

            }
        }
        return structures;
    }

    private static boolean canCreate(ServerLevel level, ChunkGenerator generator, int x, int z, Structure value) {
        return value.findValidGenerationPoint(new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),  level.getStructureManager(), level.getSeed(), new ChunkPos(x, z), level, value.biomes()::contains)).isPresent();
    }


    public DataTile getTileFromWorldCoords(int blockX, int blockZ) {
        return getTile(ChunkPos.asLong(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ)));
    }

    public DataTile getTile(int x, int z) {
        return getTile(ChunkPos.asLong(x, z));
    }

    public DataTile getTile(long pos) {
        DataTile value = this.dataTiles.get(pos);

        if (value == null) {
            try {
                CompoundTag read = null; //ioWorker.load(new ChunkPos(pos));
                if (read == null) {
                    value = new DataTile(pos, this);
                } else {
                    value = new DataTile(pos, this, read);
                }
            } catch (Exception e) {
                WorldViewer.LOGGER.error("Couldn't read file for tile [%s, %s]. ".formatted(ChunkPos.getX(pos), ChunkPos.getZ(pos)) + e.getMessage());
                value = new DataTile(pos, this);
            }
            this.dataTiles.put(pos, value);
        }
        return value;
    }

    public void unloadTileFromWorldCoords(int blockX, int blockZ) {
        unloadTile(ChunkPos.asLong(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ)));
    }

    public void unloadTile(int x, int z) {
        unloadTile(ChunkPos.asLong(x, z));
    }

    public void unloadTile(long pos) {
        @Nullable DataTile remove = this.dataTiles.remove(pos);
        if (remove != null) {
            if (remove.isNeedsSaving()) {
//                save(remove);
            }
        } else {
//            throw new RuntimeException("Trying to unloading tile that doesnt exist.");
        }
    }

    private void save(@NotNull DataTile toSave) {
        CompoundTag save = toSave.save();
        long pos = toSave.getPos();
        ioWorker.store(new ChunkPos(pos), save);
    }

    public void saveAllTiles(boolean closeWorker) {
        this.dataTiles.forEach((pos, value) -> {
            if (value.isNeedsSaving()) {
                save(value);
            }
        });
        // TODO: This is probably wrong, but we need to NOT block render thread when closing/saving all chunks bc it freezes the game.
        CompletableFuture.runAsync(() -> {
            ioWorker.synchronize(true).join();
        }, SAVE_THREAD);
    }

    public void close() {
//        saveAllTiles(true);
    }
}
