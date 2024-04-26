package dev.corgitaco.worldviewer;

import dev.corgitaco.worldviewer.client.render.WorldViewerRenderer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;

public class WorldViewerFabric implements ModInitializer {

    @Override
    public void onInitialize() {

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (Minecraft.getInstance().screen instanceof WorldViewerRenderer.Access access) {
                access.worldViewerRenderer().loadChunk(chunk.getPos().x, chunk.getPos().z);
            }
            if (Minecraft.getInstance().gui instanceof WorldViewerRenderer.Access access) {
                WorldViewerRenderer worldViewerRenderer = access.worldViewerRenderer();
                if (worldViewerRenderer != null) {
                    worldViewerRenderer.loadChunk(chunk.getPos().x, chunk.getPos().z);
                }
            }
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (Minecraft.getInstance().screen instanceof WorldViewerRenderer.Access access) {
                access.worldViewerRenderer().unloadChunk(chunk.getPos().x, chunk.getPos().z);
            }
            if (Minecraft.getInstance().gui instanceof WorldViewerRenderer.Access access) {

                WorldViewerRenderer worldViewerRenderer = access.worldViewerRenderer();
                if (worldViewerRenderer != null) {
                    worldViewerRenderer.unloadChunk(chunk.getPos().x, chunk.getPos().z);
                }
            }
        });

        CommonClass.init();
    }
}
