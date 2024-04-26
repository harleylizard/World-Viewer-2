package dev.corgitaco.worldviewer.client.tile.tilelayer;


import dev.corgitaco.worldviewer.client.tile.RenderTileContext;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StructuresLayer extends TileLayer {

    @Nullable
    private final Map<Holder<Structure>, LongSet> positionsForStructure;

    public StructuresLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet loadedChunks, @Nullable StructuresLayer lowerResolution) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, loadedChunks, lowerResolution);

        Map<Holder<Structure>, LongSet> positionsForStructure = new Reference2ObjectOpenHashMap<>();
        for (int x = 0; x < SectionPos.blockToSectionCoord(size * sampleResolution); x++) {
            for (int z = 0; z < SectionPos.blockToSectionCoord(size * sampleResolution); z++) {
                int chunkX = SectionPos.blockToSectionCoord(tileWorldX) + x;
                int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) + z;
                if (Thread.currentThread().isInterrupted()) {
                    this.positionsForStructure = null;
                    return;
                }

                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                loadedChunks.add(chunkKey);
                for (Holder<Structure> structure : tileManager.getStructures(chunkX, chunkZ)) {
                    positionsForStructure.computeIfAbsent(structure, configuredStructureFeatureHolder -> new LongArraySet()).add(chunkKey);
                }
            }
        }
        this.positionsForStructure = positionsForStructure;
    }

    public Long2ObjectMap<DynamicTexture> spriteRenderer(RenderTileContext renderTileContext) {
        if (this.positionsForStructure != null) {
            Long2ObjectMap<DynamicTexture> renderer = new Long2ObjectLinkedOpenHashMap<>();

            this.positionsForStructure.forEach(((configuredStructureFeatureHolder, longs) -> {
                DynamicTexture dynamicTexture = renderTileContext.iconLookup().getTextures().get(Registries.STRUCTURE).get(configuredStructureFeatureHolder.unwrapKey().orElseThrow());
                if (dynamicTexture == null) {
                    return;
                }

                for (long structureChunkPos : longs) {
                    int structureWorldX = SectionPos.sectionToBlockCoord(ChunkPos.getX(structureChunkPos)) + 7;
                    int structureWorldZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(structureChunkPos)) + 7;

                    renderer.computeIfAbsent(ChunkPos.asLong(structureWorldX, structureWorldZ), key -> dynamicTexture);
                }
            }));

            return renderer;
        }

        return Long2ObjectMaps.emptyMap();
    }


    @Override
    public @Nullable List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY, int mouseTileImageLocalX, int mouseTileImageLocalY) {
        if (this.positionsForStructure != null) {
            StringBuilder structures = new StringBuilder();
            this.positionsForStructure.forEach((configuredStructureFeatureHolder, longs) -> {
                long mouseChunk = ChunkPos.asLong(SectionPos.blockToSectionCoord(mouseWorldX), SectionPos.blockToSectionCoord(mouseWorldZ));
                if (longs.contains(mouseChunk)) {
                    if (!structures.isEmpty()) {
                        structures.append(", ");
                    }
                    structures.append(configuredStructureFeatureHolder.unwrapKey().orElseThrow().location().toString());
                }
            });
            return Collections.singletonList(Component.literal("Structure(s): %s".formatted(structures.toString())));
        }
        return Collections.emptyList();
    }

    @Override
    public boolean usesLod() {
        return false;
    }

    @Override
    public @Nullable int[] image() {
        return null;
    }

    @Override
    public boolean isComplete() {
        return this.positionsForStructure != null;
    }
}
