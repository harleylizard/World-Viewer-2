package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.io.IOException;
import java.util.Optional;

public class StructureIconRenderer implements AutoCloseable {

    private final Object2ObjectOpenHashMap<Holder<Structure>, DynamicTexture> structureRendering = new Object2ObjectOpenHashMap<>();
    private final ServerLevel level;

    public StructureIconRenderer(ServerLevel level) {
        this.level = level;
    }

    public void init() {
        computeStructureRenderers(this.level);
    }

    private void computeStructureRenderers(ServerLevel level) {
        var random = level.random;
        level.getChunkSource().getGeneratorState().possibleStructureSets().stream().map(Holder::value).map(StructureSet::structures).forEach(structureSelectionEntries -> {
            for (StructureSet.StructureSelectionEntry structureSelectionEntry : structureSelectionEntries) {
                Holder<Structure> structure = structureSelectionEntry.structure();
                var r = Mth.randomBetweenInclusive(random, 25, 256);
                var g = Mth.randomBetweenInclusive(random, 25, 256);
                var b = Mth.randomBetweenInclusive(random, 25, 256);

                ResourceLocation location = structure.unwrapKey().orElseThrow().location();

                if (!structureRendering.containsKey(structure)) {
                    ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
                    ResourceLocation resourceLocation = new ResourceLocation(location.getNamespace(), "worldviewer/icon/structure/" + location.getPath() + ".png");

                    Optional<Resource> resource = resourceManager.getResource(resourceLocation);
                    if (resource.isPresent()) {

                        DynamicTexture texture;
                        try {
                            texture = new DynamicTexture(NativeImage.read(resource.get().open()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        this.structureRendering.put(structure, texture);
                    }
                }
            }
        });
    }

    public Object2ObjectOpenHashMap<Holder<Structure>, DynamicTexture> getStructureRendering() {
        return structureRendering;
    }

    @Override
    public void close() {
        for (DynamicTexture closeable : this.structureRendering.values()) {
            try {
                closeable.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
