package dev.corgitaco.worldviewer.client.tile;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.corgitaco.worldviewer.client.CloseCheck;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.mixin.NativeImageAccessor;
import dev.corgitaco.worldviewer.platform.ModPlatform;
import dev.corgitaco.worldviewer.util.LongPackingUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class RenderTileManager {
    private ExecutorService executorService = createExecutor("Screen-Tile-Generator");
    private static final ExecutorService FILE_SAVING_EXECUTOR_SERVICE = RenderTileManager.createExecutor(2, "Worker-TileSaver-IO");

    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<SingleScreenTileLayer>>[] trackedTileLayerFutures = Util.make(new Long2ObjectLinkedOpenHashMap[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new Long2ObjectLinkedOpenHashMap();
        }
    });
    private RenderTileContext renderTileContext;
    private final BlockPos origin;

    private final AtomicBoolean[] changesDetected = Util.make(new AtomicBoolean[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new AtomicBoolean();
        }
    });

    public final Long2ObjectOpenHashMap<SingleScreenTileLayer>[] loaded = Util.make(new Long2ObjectOpenHashMap[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new Long2ObjectOpenHashMap();
        }
    });
    public final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<ScreenTileLayer>>[] toRender = Util.make(new Int2ObjectOpenHashMap[TileLayer.FACTORY_REGISTRY.size()], maps -> {
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new Int2ObjectOpenHashMap();
        }
    });

    private final DataTileManager dataTileManager;
    private MutableInt shiftingManagerIdx;


    public boolean blockGeneration = true;

    public RenderTileManager(RenderTileContext renderTileContext, ServerLevel level, BlockPos origin, TileCoordinateShiftingManager[] shiftingManagers, MutableInt shiftingManagerIdx) {
        this.renderTileContext = renderTileContext;
        this.origin = origin;
        dataTileManager = new DataTileManager(ModPlatform.INSTANCE.configPath().resolve(String.valueOf(level.getSeed())), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());
        this.shiftingManagerIdx = shiftingManagerIdx;
        long originTile = renderTileContext.currentShiftingManager().tileKey(origin);
        loadTiles(renderTileContext, originTile);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        for (int toRenderIDX = 0; toRenderIDX < this.toRender.length; toRenderIDX++) {
            String name = TileLayer.FACTORY_REGISTRY.get(toRenderIDX).name();
            this.toRender[toRenderIDX].forEach((scale, tiles) -> renderTiles(guiGraphics, this.renderTileContext.opacities().getOrDefault(name, 1F), this.renderTileContext, tiles.values()));
        }

        for (int toRenderIDX = 0; toRenderIDX < this.loaded.length; toRenderIDX++) {
            String name = TileLayer.FACTORY_REGISTRY.get(toRenderIDX).name();
            renderTilesAfter(guiGraphics, this.renderTileContext.opacities().getOrDefault(name, 1F), this.renderTileContext, this.loaded[toRenderIDX].values());
        }
    }

    private static void renderTiles(GuiGraphics graphics, float opacity, RenderTileContext renderTileContext, Collection<? extends ScreenTileLayer> renderTiles) {
        PoseStack poseStack = graphics.pose();
        renderTiles.forEach(tileToRender -> {
            if (tileToRender != null) {
                int localX = (int) renderTileContext.localXFromWorldX(tileToRender.getMinTileWorldX());
                int localZ = (int) renderTileContext.localZFromWorldZ(tileToRender.getMinTileWorldZ());

                int screenTileMinX = (renderTileContext.getScreenCenterX() + localX);
                int screenTileMinZ = (renderTileContext.getScreenCenterZ() + localZ);

                poseStack.pushPose();
                poseStack.translate(screenTileMinX, screenTileMinZ, 0);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                tileToRender.renderTile(graphics, renderTileContext.scale(), opacity, renderTileContext);
                poseStack.popPose();
            }
        });
    }

    private static void renderTilesAfter(GuiGraphics graphics, float opacity, RenderTileContext renderTileContext, Collection<? extends SingleScreenTileLayer> renderTiles) {
        PoseStack poseStack = graphics.pose();
        renderTiles.forEach(tileToRender -> {
            if (tileToRender != null) {
                int localX = (int) renderTileContext.localXFromWorldX(tileToRender.getMinTileWorldX());
                int localZ = (int) renderTileContext.localZFromWorldZ(tileToRender.getMinTileWorldZ());

                int screenTileMinX = (renderTileContext.getScreenCenterX() + localX);
                int screenTileMinZ = (renderTileContext.getScreenCenterZ() + localZ);

                poseStack.pushPose();
                poseStack.translate(screenTileMinX, screenTileMinZ, 0);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                tileToRender.afterTilesRender(graphics, renderTileContext.scale(), opacity, renderTileContext);
                poseStack.popPose();
            }
        });
    }

    public void tick() {
        long originTile = renderTileContext.currentShiftingManager().tileKey(this.origin);
        if (!blockGeneration) {
            loadTiles(renderTileContext, originTile);
            blockGeneration = true;
        }

        List<Runnable> toRun = new ArrayList<>();

        processLayers(toRun);

        toRun.forEach(Runnable::run);

    }

    private void processLayers(List<Runnable> toRun) {
        for (int trackedTileLayerFutureIdx = 0; trackedTileLayerFutureIdx < trackedTileLayerFutures.length; trackedTileLayerFutureIdx++) {
            if (!changesDetected[trackedTileLayerFutureIdx].get()) {
                continue;
            }
            changesDetected[trackedTileLayerFutureIdx].set(false);
            {
                long startMs = System.currentTimeMillis();
                processFutures(trackedTileLayerFutureIdx, toRun);
                long endTime = System.currentTimeMillis();
                long timeTaken = endTime - startMs;
                if (timeTaken > 5) {
                    System.out.println("Processing futures took over 5ms, took: %s".formatted(timeTaken));
                }
            }

            {
                long startMs = System.currentTimeMillis();
                scaleUpTiles(trackedTileLayerFutureIdx, toRun);
                long endTime = System.currentTimeMillis();
                long timeTaken = endTime - startMs;
                if (timeTaken > 5) {
                    System.out.println("Scaling tiles took over 5ms, took: %s".formatted(timeTaken));
                }
            }
        }
    }

    private void processFutures(final int trackedTileLayerFutureIdx, List<Runnable> toRun) {
        LongSet toRemove = new LongOpenHashSet();
        int finalidx = trackedTileLayerFutureIdx;
        trackedTileLayerFutures[finalidx].long2ObjectEntrySet().fastForEach(entry -> {
            long tilePos = entry.getLongKey();
            CompletableFuture<SingleScreenTileLayer> future = entry.getValue();
            if (future.isCompletedExceptionally()) {
                try {
                    SingleScreenTileLayer now = future.getNow(null);
                    if (now != null) {
                        if (now.canClose()) {
                            now.closeAll();
                        } else {
                            now.releaseDynamicTextureID();
                            now.setShouldClose(true);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            if (future.isDone()) {
                toRemove.add(tilePos);
            }

            int worldX = renderTileContext.currentShiftingManager().getWorldXFromTileKey(tilePos);
            int worldZ = renderTileContext.currentShiftingManager().getWorldZFromTileKey(tilePos);
            if (!renderTileContext.worldViewArea().intersects(worldX, worldZ, worldX, worldZ) && !future.isCancelled()) {
                future.cancel(true);
                toRemove.add(tilePos);
            } else {
                toRun.add(() -> {
                    SingleScreenTileLayer lastResolution = future.getNow(null);
                    if (lastResolution != null) {
                        int newSampleRes = lastResolution.getSampleRes() >> 1;
                        if (newSampleRes >= renderTileContext.currentShiftingManager().sampleResolution()) {
                            submitTileFuture(renderTileContext, lastResolution.getSize(), tilePos, newSampleRes, lastResolution, finalidx);
                        }

                        SingleScreenTileLayer previous = loaded[finalidx].put(tilePos, lastResolution);
                        this.toRender[finalidx].computeIfAbsent(1, key1 -> new Long2ObjectOpenHashMap<>()).put(tilePos, lastResolution);
                        if (previous != null && previous != lastResolution && previous.tileLayer().usesLod()) {
                            if (previous.canClose()) {
                                previous.closeAll();
                            } else {
                                previous.releaseDynamicTextureID();
                                previous.setShouldClose(true);
                            }
                        }
                    }
                });
            }
        });
        toRemove.forEach(k -> this.trackedTileLayerFutures[finalidx].remove(k));
    }

    private void scaleUpTiles(final int trackedTileLayerFutureIdx, List<Runnable> toRun) {
        Int2ObjectOpenHashMap<LongSet> uploaded = new Int2ObjectOpenHashMap<>();
        this.toRender[trackedTileLayerFutureIdx].int2ObjectEntrySet().fastForEach((entry) -> {
            int currentScale = entry.getIntKey();
            Long2ObjectOpenHashMap<ScreenTileLayer> tiles = entry.getValue();

            int newScale = currentScale << 1;

            tiles.long2ObjectEntrySet().fastForEach(screenTileEntry -> {
                long tilePos = screenTileEntry.getLongKey();
                if (uploaded.computeIfAbsent(currentScale, key -> new LongOpenHashSet()).contains(tilePos)) {
                    return;
                }

                ScreenTileLayer screenTileLayer = screenTileEntry.getValue();

                if (screenTileLayer.image() == null) {
                    return;
                }

                int tileX = renderTileContext.currentShiftingManager().getTileX(tilePos);
                int tileZ = renderTileContext.currentShiftingManager().getTileZ(tilePos);

                int getNextScaleMinTileX = (tileX / newScale) * newScale;
                int getNextScaleMaxTileX = (getNextScaleMinTileX + newScale);

                int getNextScaleMinTileZ = (tileZ / newScale) * newScale;
                int getNextScaleMaxTileZ = (getNextScaleMinTileZ + newScale);

                long minTileKey = LongPackingUtil.tileKey(getNextScaleMinTileX, getNextScaleMinTileZ);

                if (tilePos != minTileKey) {
                    return;
                }

                if (!toRender[trackedTileLayerFutureIdx].computeIfAbsent(newScale, key1 -> new Long2ObjectOpenHashMap<>()).containsKey(minTileKey)) {
                    int xRange = Math.abs(getNextScaleMaxTileX - getNextScaleMinTileX);
                    int zRange = Math.abs(getNextScaleMaxTileZ - getNextScaleMinTileZ);

                    int xTileIncrement = xRange / 2;
                    int zTileIncrement = zRange / 2;

                    ScreenTileLayer[][] tilesToRender = new ScreenTileLayer[2][2];

                    long[] positions = new long[2 * 2];
                    for (int tileOffsetX = 0; tileOffsetX < 2; tileOffsetX++) {
                        for (int tileOffsetZ = 0; tileOffsetZ < 2; tileOffsetZ++) {
                            int tileX1 = getNextScaleMinTileX + (tileOffsetX * xTileIncrement);
                            int tileZ1 = getNextScaleMinTileZ + (tileOffsetZ * zTileIncrement);
                            long tileKey = LongPackingUtil.tileKey(tileX1, tileZ1);
                            positions[tileOffsetX * 2 + tileOffsetZ] = tileKey;

                            ScreenTileLayer offset = this.toRender[trackedTileLayerFutureIdx].get(currentScale).get(tileKey);

                            if (offset != null && offset.sampleResCheck(renderTileContext.currentShiftingManager().sampleResolution())) {
                                tilesToRender[tileOffsetX][tileOffsetZ] = offset;
                            } else {
                                return;
                            }
                        }
                    }

                    for (long position : positions) {
                        uploaded.computeIfAbsent(currentScale, key -> new LongOpenHashSet()).add(position);
                    }
                    uploaded.computeIfAbsent(newScale, key -> new LongOpenHashSet()).add(minTileKey);

                    toRun.add(() -> {
                        toRender[trackedTileLayerFutureIdx].computeIfAbsent(newScale, key1 -> new Long2ObjectOpenHashMap<>()).put(minTileKey, new MultiScreenTileLayer(tilesToRender));
                        for (long pos : positions) {
                            toRender[trackedTileLayerFutureIdx].get(currentScale).remove(pos);
                        }
                    });
                }
            });
        });
    }

    private void loadTiles(RenderTileContext worldScreenv2, long originTile) {
        int xTileRange = worldScreenv2.xTileRange();
        int zTileRange = worldScreenv2.zTileRange();

        int slices = 360;
        double sliceSize = Mth.TWO_PI / slices;

        int tileRange = Math.max(xTileRange, zTileRange) + 2;
        for (int tileDistanceFromOrigin = 0; tileDistanceFromOrigin <= tileRange; tileDistanceFromOrigin++) {

            int tileSize = worldScreenv2.currentShiftingManager().tileSize();
            int originWorldX = worldScreenv2.currentShiftingManager().getWorldXFromTileKey(originTile) + (tileSize / 2);
            int originWorldZ = worldScreenv2.currentShiftingManager().getWorldZFromTileKey(originTile) + (tileSize / 2);
            double distance = tileSize * tileDistanceFromOrigin;

            for (int i = 0; i < slices; i++) {
                double angle = i * sliceSize;
                int worldTileX = (int) Math.round(originWorldX + (Math.sin(angle) * distance));
                int worldTileZ = (int) Math.round(originWorldZ + (Math.cos(angle) * distance));
                if (worldScreenv2.worldViewArea().intersects(worldTileX, worldTileZ, worldTileX, worldTileZ)) {
                    long tilePos = LongPackingUtil.tileKey(worldScreenv2.currentShiftingManager().blockToTile(worldTileX), worldScreenv2.currentShiftingManager().blockToTile(worldTileZ));

                    for (int trackedTileLayerFuture = 0; trackedTileLayerFuture < this.trackedTileLayerFutures.length; trackedTileLayerFuture++) {
                        SingleScreenTileLayer tile = loaded[trackedTileLayerFuture].get(tilePos);
                        if (tile == null) {
                            submitTileFuture(worldScreenv2, tileSize, tilePos, worldScreenv2.currentShiftingManager().sampleResolution() << 3, null, trackedTileLayerFuture);
                        }
                    }
                }
            }
        }
    }

    private void submitTileFuture(RenderTileContext renderTileContext, int tileSize, long tilePos, int sampleResolution, @Nullable SingleScreenTileLayer lastResolution, final int layerIdx) {
        trackedTileLayerFutures[layerIdx].computeIfAbsent(tilePos, key -> CompletableFuture.supplyAsync(() -> {
            var worldMinTileX = renderTileContext.currentShiftingManager().getWorldXFromTileKey(tilePos);
            var worldMinTileZ = renderTileContext.currentShiftingManager().getWorldZFromTileKey(tilePos);

            String levelName = this.dataTileManager.serverLevel().getServer().getWorldData().getLevelName();
            String name = TileLayer.FACTORY_REGISTRY.get(layerIdx).name();
            TileLayer.GenerationFactory generationFactory = TileLayer.FACTORY_REGISTRY.get(layerIdx).generationFactory();


            Path imagePath = ModPlatform.INSTANCE.configPath().resolve("client").resolve("map").resolve(levelName).resolve(name).resolve("image").resolve("p." + renderTileContext.currentShiftingManager().blockToTile(worldMinTileX) + "-" + renderTileContext.currentShiftingManager().blockToTile(worldMinTileZ) + "_s." + tileSize + ".png");
            Path dataPath = ModPlatform.INSTANCE.configPath().resolve("client").resolve("map").resolve(levelName).resolve(name).resolve("data").resolve("p." + renderTileContext.currentShiftingManager().blockToTile(worldMinTileX) + "-" + renderTileContext.currentShiftingManager().blockToTile(worldMinTileZ) + "_s." + tileSize + ".dat");
            LongSet sampledChunks = new LongOpenHashSet();
            TileLayer.DiskFactory diskFactory = TileLayer.FACTORY_REGISTRY.get(layerIdx).diskFactory();

            TileLayer tileLayer = getTileLayer(renderTileContext, tileSize, sampleResolution, lastResolution, diskFactory, imagePath, dataPath, generationFactory, worldMinTileX, worldMinTileZ, sampledChunks);

            SingleScreenTileLayer tile = new SingleScreenTileLayer(tileLayer, worldMinTileX, worldMinTileZ, tileSize);
            changesDetected[layerIdx].set(true);

            sampledChunks.forEach(this.dataTileManager::unloadTile);
            return tile;
        }, executorService));
    }

    private TileLayer getTileLayer(RenderTileContext renderTileContext, int tileSize, int sampleResolution, @Nullable SingleScreenTileLayer lastResolution, TileLayer.DiskFactory diskFactory, Path imagePath, Path dataPath, TileLayer.GenerationFactory generationFactory, int x, int z, LongSet sampledChunks) {
        TileLayer tileLayer;
        if (lastResolution != null) {
            TileLayer lastResTileLayer = lastResolution.tileLayer();
            if (!lastResTileLayer.usesLod()) {
                lastResTileLayer.setSampleResolution(sampleResolution);
                return lastResTileLayer;
            }

            tileLayer = lastResTileLayer;
        } else {
            tileLayer = readTileLayerFromDisk(tileSize, sampleResolution, diskFactory, imagePath, dataPath);
            if (tileLayer != null && tileLayer.isComplete()) {
                return tileLayer;
            }
        }

        boolean nullTileLayer = tileLayer == null;

        if (nullTileLayer) {
            tileLayer = generateTile(generationFactory, 63, x, z, tileSize, sampleResolution, dataPath, imagePath, sampledChunks);
        } else {
            if (!tileLayer.isComplete()) {
                tileLayer.close();
                tileLayer = generateTile(generationFactory, 63, x, z, tileSize, sampleResolution, dataPath, imagePath, sampledChunks);
            } else {
                boolean resolutionsDontMatch = tileLayer.sampleRes() != renderTileContext.currentShiftingManager().sampleResolution();
                boolean usesLod = tileLayer.usesLod();
                if (usesLod && resolutionsDontMatch) {
                    tileLayer.close();
                    tileLayer = generateTile(generationFactory, 63, x, z, tileSize, tileLayer.sampleRes() >> 1, dataPath, imagePath, sampledChunks);
                }
            }

        }
        return tileLayer;
    }

    private static TileLayer readTileLayerFromDisk(int tileSize, int sampleResolution, @Nullable TileLayer.DiskFactory diskFactory, Path imagePath, Path dataPath) {
        TileLayer tileLayer = null;
        if (diskFactory != null) {
            try {
                TileLayer fromDisk = diskFactory.fromDisk(tileSize, imagePath, dataPath, sampleResolution);
                tileLayer = fromDisk;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return tileLayer;
    }

    private TileLayer generateTile(TileLayer.GenerationFactory generationFactory, int scrollY, int minTileWorldX, int minTileWorldZ, int size, int sampleRes, Path dataPath, Path imagePath, LongSet sampledChunks) {
        if (sampleRes < 1) {
            throw new IllegalArgumentException("Sample resolution must at least 1 to generate a tile layer.");
        }
        TileLayer tileLayer1 = generationFactory.make(this.dataTileManager, scrollY, minTileWorldX, minTileWorldZ, size, sampleRes, sampledChunks);
        CompoundTag tag = tileLayer1.tag();
        if (tag != null && tileLayer1.isComplete()) {

            FILE_SAVING_EXECUTOR_SERVICE.submit(() -> {
                try {
                    ByteArrayDataOutput byteArrayDataOutput = ByteStreams.newDataOutput();
                    NbtIo.write(tag, byteArrayDataOutput);
                    File dataPathFile = dataPath.toFile();
                    if (dataPathFile.exists()) {
                        while (!dataPathFile.canWrite()) {
                            Thread.sleep(1);
                        }
                    } else {
                        Path parent = dataPath.getParent();
                        if (!parent.toFile().exists()) {
                            Files.createDirectories(parent);
                        }
                    }
                    Files.write(dataPath, byteArrayDataOutput.toByteArray());
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        NativeImage image = tileLayer1.image();
        if (image != null) {
            if (((NativeImageAccessor) (Object) image).wvGetPixels() != 0L) {
                ((CloseCheck) (Object) image).setCanClose(false);
                FILE_SAVING_EXECUTOR_SERVICE.submit(() -> {
                    try {
                        byte[] imageByteArray = image.asByteArray();
                        File imagePathFile = imagePath.toFile();
                        if (imagePathFile.exists()) {
                            while (!imagePathFile.canWrite()) {
                                Thread.sleep(1);
                            }
                        } else {
                            Path parent = imagePath.getParent();
                            if (!parent.toFile().exists()) {
                                Files.createDirectories(parent);
                            }
                        }

                        Files.write(imagePath, imageByteArray);
                        ((CloseCheck) (Object) image).setCanClose(true);

                        if (((CloseCheck) (Object) image).shouldClose()) {
                            image.close();
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
        return tileLayer1;
    }

    public void cull(WorldScreenv2 worldScreenv2) {
        for (int loadedIdx = 0; loadedIdx < this.loaded.length; loadedIdx++) {
            LongSet toRemove = new LongOpenHashSet();

            this.loaded[loadedIdx].forEach((pos, tile) -> {
                int minTileWorldX = tile.getMinTileWorldX();
                int minTileWorldZ = tile.getMinTileWorldZ();
                int maxTileWorldX = tile.getMaxTileWorldX();
                int maxTileWorldZ = tile.getMaxTileWorldZ();
                if (!worldScreenv2.worldViewArea.intersects(minTileWorldX, minTileWorldZ, maxTileWorldX, maxTileWorldZ)) {
                    toRemove.add(pos);
                }
            });


            Int2ObjectOpenHashMap<LongList> toRemoveRender = new Int2ObjectOpenHashMap<>();

            this.toRender[loadedIdx].int2ObjectEntrySet().fastForEach(entry -> {
                int scale = entry.getIntKey();
                Long2ObjectOpenHashMap<ScreenTileLayer> tiles = entry.getValue();
                LongList longs = toRemoveRender.computeIfAbsent(scale, key -> new LongArrayList());

                tiles.long2ObjectEntrySet().fastForEach(screenTileEntry -> {
                    long tilePos = screenTileEntry.getLongKey();
                    ScreenTileLayer tile = screenTileEntry.getValue();

                    int minTileWorldX = tile.getMinTileWorldX();
                    int minTileWorldZ = tile.getMinTileWorldZ();
                    int maxTileWorldX = tile.getMaxTileWorldX();
                    int maxTileWorldZ = tile.getMaxTileWorldZ();
                    if (!worldScreenv2.worldViewArea.intersects(minTileWorldX, minTileWorldZ, maxTileWorldX, maxTileWorldZ)) {
                        if (tile.canClose()) {
                            tile.closeAll();
                        } else {
                            tile.releaseDynamicTextureID();
                            tile.setShouldClose(true);
                        }
                        longs.add(tilePos);
                    }
                });
            });

            int finalLoadedIdx = loadedIdx;
            toRemoveRender.int2ObjectEntrySet().fastForEach(longListEntry -> {
                int scale = longListEntry.getIntKey();
                LongList tiles = longListEntry.getValue();
                Long2ObjectOpenHashMap<ScreenTileLayer> longScreenTileMap = this.toRender[finalLoadedIdx].get(scale);
                tiles.forEach(key -> {
                    ScreenTileLayer remove = longScreenTileMap.remove(key);
                    if (remove != null) {
                        if (remove.canClose()) {
                            remove.closeAll();
                        } else {
                            remove.releaseDynamicTextureID();
                            remove.setShouldClose(true);
                        }
                    }
                });

                if (longScreenTileMap.isEmpty()) {
                    this.toRender[finalLoadedIdx].remove(scale);
                }
            });

            toRemove.forEach(loaded[finalLoadedIdx]::remove);
        }
    }

    public void close() {
        this.executorService.shutdownNow();
        for (Long2ObjectOpenHashMap<SingleScreenTileLayer> singleScreenTileLayerLong2ObjectOpenHashMap : this.loaded) {
            singleScreenTileLayerLong2ObjectOpenHashMap.clear();
        }
        this.dataTileManager.close();
    }

    public void onScroll(int delta) {
        this.executorService.shutdownNow();
        this.executorService = createExecutor("Screen-Tile-Generator-IO");
        for (int trackedTileLayerFutureIdx = 0; trackedTileLayerFutureIdx < this.trackedTileLayerFutures.length; trackedTileLayerFutureIdx++) {
            this.trackedTileLayerFutures[trackedTileLayerFutureIdx].clear();
            this.loaded[trackedTileLayerFutureIdx].clear();

        }
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
}
