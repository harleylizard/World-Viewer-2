package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import dev.corgitaco.worldviewer.client.*;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.util.WeightedEntry;
import dev.corgitaco.worldviewer.util.WeightedRunnable;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

    public void renderSprites(MultiBufferSource.BufferSource bufferSource, PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        Int2ObjectMap<Pair<DynamicTexture, LongList>> toRender = new Int2ObjectOpenHashMap<>();

        for (Long2ObjectLinkedOpenHashMap<RenderTileLayerTileRegion> regionMap : this.regions) {
            for (Long2ObjectMap.Entry<RenderTileLayerTileRegion> tileRenderRegionEntry : regionMap.long2ObjectEntrySet()) {
                RenderTileLayerTileRegion renderRegion = tileRenderRegionEntry.getValue();
                for (Long2ObjectMap.Entry<DynamicTexture> idGrabber : renderRegion.spriteRenderer(renderTileContext).long2ObjectEntrySet()) {
                    DynamicTexture dynamicTexture = idGrabber.getValue();

                    toRender.computeIfAbsent(dynamicTexture.getId(), key -> Pair.of(dynamicTexture, new LongArrayList())).getSecond().add(idGrabber.getLongKey());
                }
            }
        }


        for (Int2ObjectMap.Entry<Pair<DynamicTexture, LongList>> pairEntry : toRender.int2ObjectEntrySet()) {
            VertexConsumer buffer = bufferSource.getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(pairEntry.getIntKey(), RenderType.NO_TRANSPARENCY));
            NativeImage pixels = pairEntry.getValue().getFirst().getPixels();


            pairEntry.getValue().getSecond().forEach(packedBlockPos -> {
                int renderX = (ChunkPos.getX(packedBlockPos) >> coordinateShiftManager.scaleShift()) - (pixels.getWidth() / 2);
                int renderY = (ChunkPos.getZ(packedBlockPos) >> coordinateShiftManager.scaleShift()) - (pixels.getHeight() / 2);
                ClientUtil.blit(buffer,
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
            });


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

        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

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
                BoundingBox worldViewArea = this.renderTileContext.worldViewArea();
                int blockSize = Math.max(worldViewArea.getXSpan(), worldViewArea.getZSpan());
                if (worldViewArea.intersects(worldTileX, worldTileZ, worldTileX, worldTileZ)) {
                    for (int layerIdx = 0; layerIdx < this.trackedTileLayerFutures.length; layerIdx++) {

                        int tileXCoord = this.coordinateShiftManager.getTileCoordFromBlockCoord(worldTileX);
                        int tileZCoord = this.coordinateShiftManager.getTileCoordFromBlockCoord(worldTileZ);

                        long tilePackedPos = ChunkPos.asLong(tileXCoord, tileZCoord);

                        int finalLayerIdx = layerIdx;
                        TileLayer.TileLayerRegistryEntry<?> tileLayerRegistryEntry = TileLayer.FACTORY_REGISTRY.get(finalLayerIdx);

                        trackedTileLayerFutures[layerIdx].computeIfAbsent(tilePackedPos, key ->
                                CompletableFuture.supplyAsync(() -> {
                                    int tileMinBlockX = this.coordinateShiftManager.getBlockCoordFromTileCoord(tileXCoord);
                                    int tileMinBlockZ = this.coordinateShiftManager.getBlockCoordFromTileCoord(tileZCoord);

                                    mutableBlockPos.set(tileMinBlockX, 0, tileMinBlockZ);
                                    LongOpenHashSet sampledDataChunks = new LongOpenHashSet();

                                    SingleScreenTileLayer singleScreenTileLayer = new SingleScreenTileLayer(
                                            tileLayerRegistryEntry.generationFactory().make(
                                                    dataTileManager,
                                                    63,
                                                    tileMinBlockX,
                                                    tileMinBlockZ,
                                                    this.coordinateShiftManager.getTileImageSize(),
                                                    getSampleResolution(),
                                                    sampledDataChunks,
                                                    null
                                            ),
                                            tileMinBlockX,
                                            tileMinBlockZ,
                                            this.coordinateShiftManager.getTileBlockSize());
                                    for (Long sampledDataChunk : sampledDataChunks) {
                                        this.dataTileManager.unloadTile(sampledDataChunk);
                                    }

                                    return singleScreenTileLayer;
                                }, runnable -> executor.execute(new WeightedRunnable() { // Dirty hack to get prioritized runnables in our executor.
                                    @Override
                                    public int priority() {
                                        return ((tileLayerRegistryEntry.weight() * 100000) * (blockSize / origin.distManhattan(new BlockPos(worldTileX, 0, worldTileZ))));
                                    }

                                    @Override
                                    public void run() {
                                        runnable.run();
                                    }
                                })).whenComplete((singleScreenTileLayer, throwable) -> {
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

    public Collection<Component> toolTip(int mouseX, int mouseY, BlockPos mouseWorldPos) {
        int mouseWorldX = mouseWorldPos.getX();
        int mouseWorldZ = mouseWorldPos.getZ();
        long mouseRegion = ChunkPos.asLong(this.coordinateShiftManager.getRegionCoordFromBlockCoord(mouseWorldPos.getX()), this.coordinateShiftManager.getRegionCoordFromBlockCoord(mouseWorldPos.getZ()));

        List<Component> toolTip = new ArrayList<>();

        for (int i = 0; i < this.regions.length; i++) {
            RenderTileLayerTileRegion renderTileLayerTileRegion = this.regions[i].get(mouseRegion);
            if (renderTileLayerTileRegion != null) {
                toolTip.add(Component.literal("\"" + TileLayer.FACTORY_REGISTRY.get(i).name() + "\" " + " layer").withStyle(Style.EMPTY.withBold(true).withUnderlined(true)));
                toolTip.addAll(renderTileLayerTileRegion.toolTip(mouseX, mouseY, mouseWorldX, mouseWorldZ));
            }
        }

        return toolTip;
    }


    private int getSampleResolution() {
        return 1 << this.coordinateShiftManager.scaleShift();
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


    public static ThreadPoolExecutor createExecutor(String name) {
        return createExecutor(Mth.clamp((Runtime.getRuntime().availableProcessors() - 1) / 2, 1, 25), name);
    }

    public static ThreadPoolExecutor createExecutor(int processors, String name) {
        MutableInt count = new MutableInt(1);

        ThreadFactory threadFactory = new ThreadFactory() {
            private final ThreadFactory backing = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(@NotNull Runnable r) {
                var thread = backing.newThread(r);

                thread.setName(name + "-" + count.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };


        return new ThreadPoolExecutor(processors, processors, 0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(1000 * TileLayer.FACTORY_REGISTRY.size(), Comparator.comparingInt(value -> {

                    if (value instanceof WeightedRunnable weightedRunnable) {
                        return weightedRunnable.priority();
                    }

                    return 0;
                }).reversed()),
                threadFactory);
    }
}
