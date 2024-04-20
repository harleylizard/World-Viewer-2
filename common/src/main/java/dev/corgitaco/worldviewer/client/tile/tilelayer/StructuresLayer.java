package dev.corgitaco.worldviewer.client.tile.tilelayer;


import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.RenderTileContext;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
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

        Map<Holder<Structure>, LongSet> positionsForStructure =  new Reference2ObjectOpenHashMap<>();
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

    @Override
    public void afterTilesRender(MultiBufferSource.BufferSource bufferSource, PoseStack stack, double opacity, int tileMinWorldX, int tileMinWorldZ, RenderTileContext renderTileContext) {
        if (this.positionsForStructure != null) {
            this.positionsForStructure.forEach(((configuredStructureFeatureHolder, longs) -> {
                for (long structureChunkPos : longs) {
                    int structureWorldX = SectionPos.sectionToBlockCoord(ChunkPos.getX(structureChunkPos));
                    int structureWorldZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(structureChunkPos));


                    if (renderTileContext.worldViewArea().intersects(structureWorldX, structureWorldZ, structureWorldX, structureWorldZ)) {
                        DynamicTexture dynamicTexture = renderTileContext.structureRenderer().getStructureRendering().get(configuredStructureFeatureHolder);

                        if (dynamicTexture != null) {
                            CoordinateShiftManager coordinateShiftManager = renderTileContext.coordinateShiftManager();

                            NativeImage pixels = dynamicTexture.getPixels();

                            int renderX = (structureWorldX >> coordinateShiftManager.scaleShift()) - (pixels.getWidth() / 2);
                            int renderY = (structureWorldZ >> coordinateShiftManager.scaleShift()) - (pixels.getHeight() / 2);

                            ClientUtil.blit(
                                    bufferSource.getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(dynamicTexture.getId(), RenderType.NO_TRANSPARENCY)),
                                    stack,
                                    1,
                                    renderX,
                                    renderY,
                                    0F,
                                    0F,
                                    pixels.getWidth(),
                                    pixels.getHeight(),
                                    pixels.getWidth(),
                                    pixels.getHeight()
                            );
                        }
                    }
                }
            }));
        }
    }

    @Override
    public @Nullable List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
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
        return null;
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
