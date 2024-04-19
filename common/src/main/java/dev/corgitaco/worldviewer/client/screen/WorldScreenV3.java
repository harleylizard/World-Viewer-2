package dev.corgitaco.worldviewer.client.screen;

import com.google.common.collect.Queues;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.TileRenderRegion;
import dev.corgitaco.worldviewer.client.tile.SingleScreenTileLayer;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
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
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static dev.corgitaco.worldviewer.client.tile.RenderTileManager.createExecutor;

public class WorldScreenV3 extends Screen {
    private ExecutorService executorService = createExecutor("Screen-Tile-Generator");


    private final ArrayBlockingQueue<Runnable> tileSubmissionsQueue = Queues.newArrayBlockingQueue(1000);


    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<SingleScreenTileLayer>>[] trackedTileLayerFutures = Util.make(new Long2ObjectLinkedOpenHashMap[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new Long2ObjectLinkedOpenHashMap<>();
        }
    });

    private final Long2ObjectLinkedOpenHashMap<TileRenderRegion>[] regions = Util.make(new Long2ObjectLinkedOpenHashMap[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new Long2ObjectLinkedOpenHashMap<>();
        }
    });


    private DataTileManager dataTileManager;

    public BoundingBox worldViewArea;


    private final CoordinateShiftManager coordinateShiftManager = new CoordinateShiftManager(10, 5);
    public final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();

    public WorldScreenV3(Component $$0) {
        super($$0);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack poseStack = guiGraphics.pose();

        poseStack.pushPose();
        poseStack.translate(getScreenCenterX(), getScreenCenterZ(), 0);
        poseStack.translate(getOriginRenderOffsetX(), getOriginRenderOffsetZ(), 0);


        for (Long2ObjectLinkedOpenHashMap<TileRenderRegion> regionMap : this.regions) {
            for (Long2ObjectMap.Entry<TileRenderRegion> tileRenderRegionEntry : regionMap.long2ObjectEntrySet()) {
                TileRenderRegion renderRegion = tileRenderRegionEntry.getValue();
                renderRegion.render(guiGraphics);
            }
        }

        poseStack.popPose();

    }


    private int getOriginRenderOffsetX() {
        return getOriginRenderOffset(origin.getX());
    }

    private int getOriginRenderOffsetZ() {
        return getOriginRenderOffset(origin.getZ());
    }

    private int getOriginRenderOffset(int origin) {
        return -(origin >> this.coordinateShiftManager.scaleShift());
    }

    @Override
    protected void init() {
        super.init();
        this.origin.set(Minecraft.getInstance().player.blockPosition());
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.getLevel(Minecraft.getInstance().level.dimension());

        dataTileManager = new DataTileManager(ModPlatform.INSTANCE.configPath().resolve(String.valueOf(level.getSeed())), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());


        this.worldViewArea = BoundingBox.fromCorners(
                new Vec3i(
                        (int) Math.max(level.getWorldBorder().getMinX(), this.origin.getX() - this.coordinateShiftManager.getBlockCoordFromTileCoord(getScreenXTileRange()) - 1),
                        level.getMinBuildHeight(),
                        (int) Math.max(level.getWorldBorder().getMinZ(), this.origin.getZ() - this.coordinateShiftManager.getBlockCoordFromTileCoord(getScreenZTileRange()) - 1)
                ),
                new Vec3i(
                        (int) Math.min(level.getWorldBorder().getMaxX(), this.origin.getX() + this.coordinateShiftManager.getBlockCoordFromTileCoord(getScreenXTileRange()) + 1),
                        level.getMaxBuildHeight(),
                        (int) Math.min(level.getWorldBorder().getMaxZ(), this.origin.getZ() + this.coordinateShiftManager.getBlockCoordFromTileCoord(getScreenZTileRange()) + 1)
                )
        );
        fillRegions();
    }

    private void fillRegions() {
        int slices = 360;
        double sliceSize = Mth.TWO_PI / slices;

        int xTileRange = getScreenXTileRange();
        int zTileRange = getScreenZTileRange();

        int tileRange = Math.max(xTileRange, zTileRange) + 2;

        for (int tileDistanceFromOrigin = 0; tileDistanceFromOrigin <= tileRange; tileDistanceFromOrigin++) {
            int tileSize = this.coordinateShiftManager.getTileBlockSize();

            int originTileX = this.coordinateShiftManager.getTileCoordFromBlockCoord(this.origin.getX());
            int originTileZ = this.coordinateShiftManager.getTileCoordFromBlockCoord(this.origin.getZ());

            int originWorldX = this.coordinateShiftManager.getBlockCoordFromTileCoord(originTileX) + (tileSize / 2);
            int originWorldZ = this.coordinateShiftManager.getBlockCoordFromTileCoord(originTileZ) + (tileSize / 2);

            double distance = tileSize * tileDistanceFromOrigin;

            for (int i = 0; i < slices; i++) {
                double angle = i * sliceSize;
                int worldTileX = (int) Math.round(originWorldX + (Math.sin(angle) * distance));
                int worldTileZ = (int) Math.round(originWorldZ + (Math.cos(angle) * distance));
                if (worldViewArea.intersects(worldTileX, worldTileZ, worldTileX, worldTileZ)) {
                    for (int layerIdx = 0; layerIdx < this.trackedTileLayerFutures.length; layerIdx++) {

                        int tileXCoord = this.coordinateShiftManager.getTileCoordFromBlockCoord(worldTileX);
                        int tileZCoord = this.coordinateShiftManager.getTileCoordFromBlockCoord(worldTileZ);
                        long tilePackedPos = ChunkPos.asLong(tileXCoord, tileZCoord);

                        int finalLayerIdx = layerIdx;
                        trackedTileLayerFutures[layerIdx].computeIfAbsent(tilePackedPos, key ->
                                CompletableFuture.supplyAsync(() -> {
                                    int tileMinBlockX = this.coordinateShiftManager.getBlockCoordFromTileCoord(tileXCoord);
                                    int tileMinBlockZ = this.coordinateShiftManager.getBlockCoordFromTileCoord(tileZCoord);
                                    return new SingleScreenTileLayer(
                                            TileLayer.FACTORY_REGISTRY.get(finalLayerIdx).generationFactory().make(
                                                    dataTileManager,
                                                    63,
                                                    tileMinBlockX,
                                                    tileMinBlockZ,
                                                    this.coordinateShiftManager.getTileImageSize(),
                                                    1 << this.coordinateShiftManager.scaleShift(),
                                                    new LongOpenHashSet(),
                                                    null
                                            ),
                                            tileMinBlockX,
                                            tileMinBlockZ,
                                            this.coordinateShiftManager.getTileBlockSize());

                                }, executorService).whenComplete((singleScreenTileLayer, throwable) -> {
                                    if (throwable != null) {
                                        throwable.printStackTrace();
                                    }

                                    this.tileSubmissionsQueue.add(() -> {

                                        int minTileWorldX = singleScreenTileLayer.getMinTileWorldX();
                                        int minTileWorldZ = singleScreenTileLayer.getMinTileWorldZ();

                                        int regionX = this.coordinateShiftManager.getRegionCoordFromBlockCoord(minTileWorldX);
                                        int regionZ = this.coordinateShiftManager.getRegionCoordFromBlockCoord(minTileWorldZ);


                                        int tileX = this.coordinateShiftManager.getTileCoordFromBlockCoord(minTileWorldX);
                                        int tileZ = this.coordinateShiftManager.getTileCoordFromBlockCoord(minTileWorldZ);

                                        TileRenderRegion tileRenderRegion = regions[finalLayerIdx].computeIfAbsent(ChunkPos.asLong(regionX, regionZ), regionKey -> new TileRenderRegion(this.coordinateShiftManager, regionKey));

                                        tileRenderRegion.insertLayer(singleScreenTileLayer);
                                        this.trackedTileLayerFutures[finalLayerIdx].remove(ChunkPos.asLong(tileX, tileZ));
                                    });
                                })
                        );
                    }
                }
            }
        }

    }

    private int getScreenXTileRange() {
        return (this.coordinateShiftManager.getTileCoordFromBlockCoord(getScreenCenterX() << this.coordinateShiftManager.scaleShift()) + 2);
    }

    private int getScreenZTileRange() {
        return (this.coordinateShiftManager.getTileCoordFromBlockCoord(getScreenCenterZ() << this.coordinateShiftManager.scaleShift()) + 2);
    }

    @Override
    public void tick() {
        super.tick();


        int tasks = 0;

        while (!tileSubmissionsQueue.isEmpty() && tasks <= 100) {
            Runnable poll = tileSubmissionsQueue.poll();
            RenderSystem.recordRenderCall(poll::run);
            tasks++;

        }
    }

    @Override
    public void onClose() {

        for (Long2ObjectLinkedOpenHashMap<TileRenderRegion> region : this.regions) {
            for (Long2ObjectMap.Entry<TileRenderRegion> tileRenderRegionEntry : region.long2ObjectEntrySet()) {
                tileRenderRegionEntry.getValue().close();
            }
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
        return  ((width / 2));
    }

    public int getScreenCenterZ() {
        return  ((height / 2));
    }
}
