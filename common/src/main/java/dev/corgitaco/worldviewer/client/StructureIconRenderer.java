package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.io.IOException;
import java.util.Optional;

public class StructureIconRenderer {

    private final Object2ObjectOpenHashMap<Holder<Structure>, StructureRender> structureRendering = new Object2ObjectOpenHashMap<>();


    public StructureIconRenderer(ServerLevel level) {
        computeStructureRenderers(level);
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
                    StructureRender structureRender;
                    ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
                    ResourceLocation resourceLocation = new ResourceLocation(location.getNamespace(), "worldview/icon/structure/" + location.getPath() + ".png");

                    Optional<Resource> resource = resourceManager.getResource(resourceLocation);
                    if (resource.isPresent()) {
                        try (DynamicTexture texture = new DynamicTexture(NativeImage.read(resource.get().open()))) {

                            structureRender = (guiGraphics, minDrawX, minDrawZ, maxDrawX, maxDrawZ, opacity, scale) -> {
                                var pixels = texture.getPixels();
                                if (pixels == null) {
                                    return;
                                }

                                int drawX = (maxDrawX - minDrawX / 2);
                                int drawZ = (maxDrawZ - minDrawZ / 2);

                                int width = (int) (pixels.getWidth() / scale);
                                int height = (int) (pixels.getHeight() / scale);
                                guiGraphics.blit(resourceLocation, drawX - (width / 2), drawZ - (height / 2), 0.0F, 0.0F, width, height, width, height);
                                RenderSystem.disableBlend();
                            };

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }


                    } else {
                        structureRender = (guiGraphics, minDrawX, minDrawZ, maxDrawX, maxDrawZ, opacity, scale) -> guiGraphics.fill(minDrawX, minDrawZ, maxDrawX, maxDrawZ, FastColor.ARGB32.color((int) (255 * opacity), r, g, b));
                    }

                    this.structureRendering.put(structure, structureRender);
                }
            }
        });
    }

    public Object2ObjectOpenHashMap<Holder<Structure>, StructureRender> getStructureRendering() {
        return structureRendering;
    }

    @FunctionalInterface
    public interface StructureRender {

        void render(GuiGraphics stack, int minDrawX, int minDrawZ, int maxDrawX, int maxDrawZ, float opacity, float scale);
    }
}
