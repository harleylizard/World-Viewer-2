package dev.corgitaco.worldviewer.client.screen;

import com.google.common.collect.Queues;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.TileRenderRegion;
import dev.corgitaco.worldviewer.client.tile.SingleScreenTileLayer;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.WorldViewer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.platform.ModPlatform;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorldScreenV3 extends Screen {


    private static final ConcurrentLinkedQueue<Runnable> tileSubmissionsQueue = Queues.newConcurrentLinkedQueue();


    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<SingleScreenTileLayer>>[] trackedTileLayerFutures = Util.make(new Long2ObjectLinkedOpenHashMap[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new Long2ObjectLinkedOpenHashMap<>();
        }
    });

    private final Long2ObjectLinkedOpenHashMap<TileRenderRegion> regions = new Long2ObjectLinkedOpenHashMap<>();


    private DataTileManager dataTileManager;

    private final CoordinateShiftManager coordinateShiftManager = new CoordinateShiftManager(10);
    public final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();

    public WorldScreenV3(Component $$0) {
        super($$0);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack poseStack = guiGraphics.pose();

        poseStack.pushPose();
        poseStack.translate(getScreenCenterX(), getScreenCenterZ(), 0); // Center the screen
        poseStack.translate(-origin.getX(), -origin.getZ(), 0);

        for (Long2ObjectMap.Entry<TileRenderRegion> tileRenderRegionEntry : this.regions.long2ObjectEntrySet()) {
            TileRenderRegion renderRegion = tileRenderRegionEntry.getValue();
            renderRegion.render(guiGraphics);
        }

        poseStack.popPose();

    }

    @Override
    protected void init() {
        super.init();
        this.origin.set(Minecraft.getInstance().player.blockPosition());
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.getLevel(Minecraft.getInstance().level.dimension());

        dataTileManager = new DataTileManager(ModPlatform.INSTANCE.configPath().resolve(String.valueOf(level.getSeed())), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());

        fillRegions();

    }

    private void fillRegions() {
        int radius = 1;
        for (int regionX = -radius; regionX <= radius; regionX++) {
            for (int regionZ = -radius; regionZ <= radius; regionZ++) {
                int finalRegionZ = regionZ;
                int finalRegionX = regionX;

                long regionPos = ChunkPos.asLong(finalRegionX + this.coordinateShiftManager.getRegionCoordFromBlockCoord(origin.getX()), finalRegionZ + this.coordinateShiftManager.getRegionCoordFromBlockCoord(origin.getZ()));

                TileRenderRegion tileRenderRegion = regions.computeIfAbsent(regionPos, key -> new TileRenderRegion(this.coordinateShiftManager, regionPos));

                for (int tileX = 0; tileX < this.coordinateShiftManager.getTileSize(); tileX++) {
                    for (int tileZ = 0; tileZ < this.coordinateShiftManager.getTileSize(); tileZ++) {
                        int finalTileX = tileX;
                        int finalTileZ = tileZ;
//                        tileSubmissionsQueue.add(() -> { // TODO: Why does this explode in a queue?!?!
                            int minTileWorldX = tileRenderRegion.getRegionBlockX() + finalTileX * this.coordinateShiftManager.getTileSize();
                            int minTileWorldZ = tileRenderRegion.getRegionBlockZ() + finalTileZ * this.coordinateShiftManager.getTileSize();
                            WorldViewer.LOGGER.info(minTileWorldX + ", " + minTileWorldZ);
                            tileRenderRegion.insertLayer(new SingleScreenTileLayer(TileLayer.FACTORY_REGISTRY.get(0).generationFactory().make(dataTileManager, 63, minTileWorldX, minTileWorldZ, this.coordinateShiftManager.getTileSize(), 1, new LongOpenHashSet(), null), minTileWorldX, minTileWorldZ, this.coordinateShiftManager.getTileSize()));
//                        });

                    }
                }

            }
        }

    }

    @Override
    public void tick() {
        super.tick();


        int tasks = 0;

        while (!tileSubmissionsQueue.isEmpty() && tasks <= 1) {
            Runnable poll = tileSubmissionsQueue.poll();
            RenderSystem.recordRenderCall(poll::run);
            tasks++;

        }
    }

    @Override
    public void onClose() {
        for (Long2ObjectMap.Entry<TileRenderRegion> tileRenderRegionEntry : this.regions.long2ObjectEntrySet()) {
            tileRenderRegionEntry.getValue().close();
        }
        super.onClose();
    }

    public double localXFromWorldX(double worldX) {
        return origin.getX() - worldX;
    }

    public double localZFromWorldZ(double worldZ) {
        return origin.getZ() + worldZ;
    }

    public int getScreenCenterX() {
        return (int) ((width / 2));
    }

    public int getScreenCenterZ() {
        return (int) ((height / 2));
    }
}
