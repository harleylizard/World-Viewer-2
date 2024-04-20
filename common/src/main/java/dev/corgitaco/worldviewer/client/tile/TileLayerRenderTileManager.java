package dev.corgitaco.worldviewer.client.tile;

import com.google.common.collect.Queues;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.RenderTileLayerTileRegion;
import dev.corgitaco.worldviewer.client.WhiteBackgroundTileRegion;
import dev.corgitaco.worldviewer.client.WorldCoordSquare;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.util.WeightedEntry;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.concurrent.*;

public class TileLayerRenderTileManager implements AutoCloseable {

    private final ExecutorService executor = createExecutor("Render-Tile-Generator");
    private final PriorityBlockingQueue<WeightedEntry<Runnable>> tileSubmissionsQueue = new PriorityBlockingQueue<>(1000 * TileLayer.FACTORY_REGISTRY.size(), Comparator.comparingInt(WeightedEntry::weight));
    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<SingleScreenTileLayer>>[] trackedTileLayerFutures = Util.make(new Long2ObjectLinkedOpenHashMap[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new Long2ObjectLinkedOpenHashMap<>();
        }
    });
    private final Long2ObjectLinkedOpenHashMap<RenderTileLayerTileRegion>[] regions = Util.make(new Long2ObjectLinkedOpenHashMap[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new Long2ObjectLinkedOpenHashMap<>();
        }
    });

    private final Long2ObjectLinkedOpenHashMap<WhiteBackgroundTileRegion> whiteBackGroundRegions = new Long2ObjectLinkedOpenHashMap<>();

    private final DataTileManager dataTileManager;
    private final RenderTileContext renderTileContext;
    private final CoordinateShiftManager coordinateShiftManager;
    private final BlockPos origin;

    public TileLayerRenderTileManager(BlockPos origin, RenderTileContext renderTileContext, DataTileManager dataTileManager) {
        this.origin = origin;
        this.renderTileContext = renderTileContext;
        this.coordinateShiftManager = renderTileContext.coordinateShiftManager();
        this.dataTileManager = dataTileManager;
    }

    public void init() {
        fillRegions();
    }

    public void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        for (Long2ObjectMap.Entry<WhiteBackgroundTileRegion> tileRenderRegionEntry : this.whiteBackGroundRegions.long2ObjectEntrySet()) {
            WhiteBackgroundTileRegion renderRegion = tileRenderRegionEntry.getValue();
            renderRegion.render(bufferSource, stack);

        }
        for (Long2ObjectLinkedOpenHashMap<RenderTileLayerTileRegion> regionMap : this.regions) {
            for (Long2ObjectMap.Entry<RenderTileLayerTileRegion> tileRenderRegionEntry : regionMap.long2ObjectEntrySet()) {
                RenderTileLayerTileRegion renderRegion = tileRenderRegionEntry.getValue();
                renderRegion.render(bufferSource, stack);
            }
        }
    }

    public void renderLast(MultiBufferSource.BufferSource bufferSource, PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        for (Long2ObjectLinkedOpenHashMap<RenderTileLayerTileRegion> regionMap : this.regions) {
            for (Long2ObjectMap.Entry<RenderTileLayerTileRegion> tileRenderRegionEntry : regionMap.long2ObjectEntrySet()) {
                RenderTileLayerTileRegion renderRegion = tileRenderRegionEntry.getValue();
                renderRegion.renderLast(bufferSource, stack, this.renderTileContext);
            }
        }
    }

    public void tick() {
        int tasks = 0;

        while (!tileSubmissionsQueue.isEmpty() && tasks <= 100) {
            Runnable poll = tileSubmissionsQueue.poll().value();
            poll.run();
            tasks++;

        }
    }

    private void fillRegions() {
        int slices = 360;
        double sliceSize = Mth.TWO_PI / slices;

        int xTileRange = this.renderTileContext.xTileRange();
        int zTileRange = this.renderTileContext.zTileRange();

        int tileRange = Math.max(xTileRange, zTileRange) + 2;

        for (int tileDistanceFromOrigin = 0; tileDistanceFromOrigin <= tileRange; tileDistanceFromOrigin++) {
            int tileSize = this.coordinateShiftManager.getTileBlockSize();

            int originTileX = this.coordinateShiftManager.getTileCoordFromBlockCoord(this.origin.getX());
            int originTileZ = this.coordinateShiftManager.getTileCoordFromBlockCoord(this.origin.getZ());

            int originWorldX = this.coordinateShiftManager.getBlockCoordFromTileCoord(originTileX) + (tileSize / 2);
            int originWorldZ = this.coordinateShiftManager.getBlockCoordFromTileCoord(originTileZ) + (tileSize / 2);

            double distance = tileSize * tileDistanceFromOrigin;
            for (int layerIdx = 0; layerIdx < this.trackedTileLayerFutures.length; layerIdx++) {
                for (int i = 0; i < slices; i++) {
                    double angle = i * sliceSize;
                    int worldTileX = (int) Math.round(originWorldX + (Math.sin(angle) * distance));
                    int worldTileZ = (int) Math.round(originWorldZ + (Math.cos(angle) * distance));
                    if (this.renderTileContext.worldViewArea().intersects(worldTileX, worldTileZ, worldTileX, worldTileZ)) {

                        int tileXCoord = this.coordinateShiftManager.getTileCoordFromBlockCoord(worldTileX);
                        int tileZCoord = this.coordinateShiftManager.getTileCoordFromBlockCoord(worldTileZ);
                        long tilePackedPos = ChunkPos.asLong(tileXCoord, tileZCoord);

                        int finalLayerIdx = layerIdx;
                        TileLayer.TileLayerRegistryEntry<?> tileLayerRegistryEntry = TileLayer.FACTORY_REGISTRY.get(finalLayerIdx);
                        trackedTileLayerFutures[layerIdx].computeIfAbsent(tilePackedPos, key ->
                                CompletableFuture.supplyAsync(() -> {
                                    int tileMinBlockX = this.coordinateShiftManager.getBlockCoordFromTileCoord(tileXCoord);
                                    int tileMinBlockZ = this.coordinateShiftManager.getBlockCoordFromTileCoord(tileZCoord);
                                    return new SingleScreenTileLayer(
                                            tileLayerRegistryEntry.generationFactory().make(
                                                    dataTileManager,
                                                    63,
                                                    tileMinBlockX,
                                                    tileMinBlockZ,
                                                    this.coordinateShiftManager.getTileImageSize(),
                                                    getSampleResolution(),
                                                    new LongOpenHashSet(),
                                                    null
                                            ),
                                            tileMinBlockX,
                                            tileMinBlockZ,
                                            this.coordinateShiftManager.getTileBlockSize());

                                }, executor).whenComplete((singleScreenTileLayer, throwable) -> {
                                    if (throwable != null) {
                                        throwable.printStackTrace();
                                    }

                                    this.tileSubmissionsQueue.add(new WeightedEntry<>(() -> {

                                        int minTileWorldX = singleScreenTileLayer.getMinTileWorldX();
                                        int minTileWorldZ = singleScreenTileLayer.getMinTileWorldZ();

                                        int regionX = this.coordinateShiftManager.getRegionCoordFromBlockCoord(minTileWorldX);
                                        int regionZ = this.coordinateShiftManager.getRegionCoordFromBlockCoord(minTileWorldZ);


                                        int tileX = this.coordinateShiftManager.getTileCoordFromBlockCoord(minTileWorldX);
                                        int tileZ = this.coordinateShiftManager.getTileCoordFromBlockCoord(minTileWorldZ);

                                        long regionPos = ChunkPos.asLong(regionX, regionZ);

                                        if (singleScreenTileLayer.image() != null) {
                                            WhiteBackgroundTileRegion whiteBackgroundTileRegion = this.whiteBackGroundRegions.computeIfAbsent(regionPos, regionKey -> new WhiteBackgroundTileRegion(this.coordinateShiftManager, regionKey));

                                            whiteBackgroundTileRegion.insertTile(new WorldCoordSquare(minTileWorldX, minTileWorldZ, singleScreenTileLayer.getMaxTileWorldX(), singleScreenTileLayer.getMaxTileWorldZ()));
                                        }

                                        RenderTileLayerTileRegion tileLayerRenderRegion = regions[finalLayerIdx].computeIfAbsent(regionPos, regionKey -> new RenderTileLayerTileRegion(this.coordinateShiftManager, regionKey, tileLayerRegistryEntry.transparencyStateShard()));

                                        tileLayerRenderRegion.insertTile(singleScreenTileLayer);
                                        this.trackedTileLayerFutures[finalLayerIdx].remove(ChunkPos.asLong(tileX, tileZ));
                                    }, tileLayerRegistryEntry.weight()));
                                })
                        );
                    }
                }
            }
        }

    }

    private int getSampleResolution() {
        return 1 << this.coordinateShiftManager.scaleShift();
    }

    public static ExecutorService createExecutor(String name) {
        return createExecutor(Mth.clamp((Runtime.getRuntime().availableProcessors() - 1) / 2, 1, 25), name);
    }

    public static ExecutorService createExecutor(int processors, String name) {
        MutableInt count = new MutableInt(1);
        return Executors.newFixedThreadPool(processors, new ThreadFactory() {
            private final ThreadFactory backing = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(@NotNull Runnable r) {
                var thread = backing.newThread(r);

                thread.setName(name + "-" + count.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public void close() {
        this.tileSubmissionsQueue.clear();
        this.executor.shutdownNow();
        for (Long2ObjectLinkedOpenHashMap<RenderTileLayerTileRegion> region : this.regions) {
            for (Long2ObjectMap.Entry<RenderTileLayerTileRegion> tileRenderRegionEntry : region.long2ObjectEntrySet()) {
                tileRenderRegionEntry.getValue().close();
            }
        }

        for (Long2ObjectMap.Entry<WhiteBackgroundTileRegion> whiteBackgroundTileRegionEntry : this.whiteBackGroundRegions.long2ObjectEntrySet()) {
            WhiteBackgroundTileRegion value = whiteBackgroundTileRegionEntry.getValue();

            value.close();
        }
    }
}
