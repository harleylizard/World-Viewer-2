package dev.corgitaco.worldviewer.client.tile;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVDynamicTexture;
import dev.corgitaco.worldviewer.client.WVNativeImage;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.platform.ModPlatform;
import dev.corgitaco.worldviewer.util.LongPackingUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class RenderTileManager implements AutoCloseable {
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

    private final DataTileManager dataTileManager;
    private MutableInt shiftingManagerIdx;


    private WVDynamicTexture[] texturesToRender = new WVDynamicTexture[TileLayer.FACTORY_REGISTRY.size()];


    public boolean blockGeneration = true;

    public RenderTileManager(RenderTileContext renderTileContext, ServerLevel level, BlockPos origin, TileCoordinateShiftingManager[] shiftingManagers, MutableInt shiftingManagerIdx) {
        this.renderTileContext = renderTileContext;
        this.origin = origin;
        dataTileManager = new DataTileManager(ModPlatform.INSTANCE.configPath().resolve(String.valueOf(level.getSeed())), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());
        this.shiftingManagerIdx = shiftingManagerIdx;
        long originTile = renderTileContext.currentShiftingManager().tileKey(origin);
        loadTiles(renderTileContext, originTile);


        for (int i = 0; i < this.texturesToRender.length; i++) {
            WVNativeImage image = ClientUtil.createImage(renderTileContext.renderWidth(), renderTileContext.renderHeight(), true);

            if (i == 2) {
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int z = 0; z < image.getHeight(); z++) {
                        image.setPixelRGBA(x, z, FastColor.ABGR32.color(255, 255, 0, 0));
                    }
                }
            } else {
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int z = 0; z < image.getHeight(); z++) {
                        image.setPixelRGBA(x, z, FastColor.ABGR32.color(255, 0, 0, 0));
                    }
                }

            }

            texturesToRender[i] = new WVDynamicTexture(image);
            texturesToRender[i].upload();
        }
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        List<TileLayer.TileLayerRegistryEntry<?>> factoryRegistry = TileLayer.FACTORY_REGISTRY;
        for (int i = 0; i < factoryRegistry.size(); i++) {
            TileLayer.TileLayerRegistryEntry<?> tileLayerRegistryEntry = factoryRegistry.get(i);

            float opacity = this.renderTileContext.opacities().getOrDefault(tileLayerRegistryEntry.name(), tileLayerRegistryEntry.defaultOpacity());
            ClientUtil.blit(guiGraphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(this.texturesToRender[i].getId(), RenderType.NO_TRANSPARENCY)), guiGraphics.pose(), opacity, 0, 0, 0F, 0F, renderTileContext.renderWidth(), renderTileContext.renderHeight(), renderTileContext.renderWidth(), renderTileContext.renderHeight());
        }
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

        TileCoordinateShiftingManager tileCoordinateShiftingManager = this.renderTileContext.currentShiftingManager();
        for (int trackedTileLayerFutureIdx = 0; trackedTileLayerFutureIdx < trackedTileLayerFutures.length; trackedTileLayerFutureIdx++) {
            if (!changesDetected[trackedTileLayerFutureIdx].get()) {
                continue;
            }
            changesDetected[trackedTileLayerFutureIdx].set(false);
            {
                long startMs = System.currentTimeMillis();
                processFutures(trackedTileLayerFutureIdx, tileCoordinateShiftingManager, toRun);
                long endTime = System.currentTimeMillis();
                long timeTaken = endTime - startMs;
                if (timeTaken > 5) {
                    System.out.println("Processing futures took over 5ms, took: %s".formatted(timeTaken));
                }
            }
        }
    }

    private void processFutures(final int trackedTileLayerFutureIdx, TileCoordinateShiftingManager shiftingManager, List<Runnable> toRun) {
        LongSet toRemove = new LongOpenHashSet();
        int finalidx = trackedTileLayerFutureIdx;

        trackedTileLayerFutures[finalidx].long2ObjectEntrySet().fastForEach(entry -> {
            long tilePos = entry.getLongKey();
            CompletableFuture<SingleScreenTileLayer> future = entry.getValue();
            if (future.isCompletedExceptionally()) {
                try {
                    SingleScreenTileLayer now = future.getNow(null);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            if (future.isDone()) {
                toRemove.add(tilePos);
            }

            int worldX = shiftingManager.getWorldXFromTileKey(tilePos);
            int worldZ = shiftingManager.getWorldZFromTileKey(tilePos);
            if (!renderTileContext.worldViewArea().intersects(worldX, worldZ, worldX, worldZ) && !future.isCancelled()) {
                future.cancel(true);
                toRemove.add(tilePos);
            } else {
                toRun.add(() -> {
                    SingleScreenTileLayer lastResolution = future.getNow(null);
                    if (lastResolution != null) {
                        int newSampleRes = lastResolution.getSampleRes() >> 1;
                        if (newSampleRes >= shiftingManager.sampleResolution()) {
                            AtomicBoolean changesDetected = this.changesDetected[finalidx];
                            String name = TileLayer.FACTORY_REGISTRY.get(finalidx).name();
                            TileLayer.GenerationFactory<?> generationFactory = TileLayer.FACTORY_REGISTRY.get(finalidx).generationFactory();
                            TileLayer.DiskFactory diskFactory = TileLayer.FACTORY_REGISTRY.get(finalidx).diskFactory();
                            trackedTileLayerFutures[finalidx].computeIfAbsent(tilePos, key -> CompletableFuture.supplyAsync(submitTileFuture(shiftingManager, this.dataTileManager, generationFactory, diskFactory, name, changesDetected, lastResolution.getSize(), tilePos, newSampleRes, lastResolution), executorService));
                        }

//                        SingleScreenTileLayer previous = loaded[finalidx].put(tilePos, lastResolution);

                        if (lastResolution != null && lastResolution.image() != null) {
                            WVDynamicTexture texture = this.texturesToRender[trackedTileLayerFutureIdx];

                            int localXFromWorldX = (int) this.renderTileContext.localXFromWorldX(lastResolution.getMinTileWorldX()) + renderTileContext.getScreenCenterX();
                            int localZFromWorldZ = (int) this.renderTileContext.localZFromWorldZ(lastResolution.getMinTileWorldZ()) + renderTileContext.getScreenCenterZ();


                            int imageSize = lastResolution.size() / lastResolution.getSampleRes();
                            texture.uploadSubImageWithOffset(localXFromWorldX, localZFromWorldZ, imageSize, imageSize, lastResolution.image());
                        }
                    }
                });
            }
        });
        toRemove.forEach(k -> this.trackedTileLayerFutures[finalidx].remove(k));
    }

    private void loadTiles(RenderTileContext renderTileContext, long originTile) {
        int xTileRange = renderTileContext.xTileRange();
        int zTileRange = renderTileContext.zTileRange();

        int slices = 360;
        double sliceSize = Mth.TWO_PI / slices;

        int tileRange = Math.max(xTileRange, zTileRange) + 2;


        TileCoordinateShiftingManager shiftingManager = renderTileContext.currentShiftingManager();

        for (int tileDistanceFromOrigin = 0; tileDistanceFromOrigin <= tileRange; tileDistanceFromOrigin++) {

            int tileSize = shiftingManager.tileSize();
            int originWorldX = shiftingManager.getWorldXFromTileKey(originTile) + (tileSize / 2);
            int originWorldZ = shiftingManager.getWorldZFromTileKey(originTile) + (tileSize / 2);
            double distance = tileSize * tileDistanceFromOrigin;

            for (int i = 0; i < slices; i++) {
                double angle = i * sliceSize;
                int worldTileX = (int) Math.round(originWorldX + (Math.sin(angle) * distance));
                int worldTileZ = (int) Math.round(originWorldZ + (Math.cos(angle) * distance));
                if (renderTileContext.worldViewArea().intersects(worldTileX, worldTileZ, worldTileX, worldTileZ)) {
                    long tilePos = LongPackingUtil.tileKey(shiftingManager.blockToTile(worldTileX), shiftingManager.blockToTile(worldTileZ));

                    for (int layerIdx = 0; layerIdx < this.trackedTileLayerFutures.length; layerIdx++) {
                        SingleScreenTileLayer tile = loaded[layerIdx].get(tilePos);
                        if (tile == null) {
                            AtomicBoolean changesDetected = this.changesDetected[layerIdx];
                            String name = TileLayer.FACTORY_REGISTRY.get(layerIdx).name();
                            TileLayer.GenerationFactory<?> generationFactory = TileLayer.FACTORY_REGISTRY.get(layerIdx).generationFactory();
                            TileLayer.DiskFactory diskFactory = TileLayer.FACTORY_REGISTRY.get(layerIdx).diskFactory();

                            trackedTileLayerFutures[layerIdx].computeIfAbsent(tilePos, key ->
                                    CompletableFuture.supplyAsync(submitTileFuture(shiftingManager, this.dataTileManager, generationFactory, diskFactory, name, changesDetected, tileSize, tilePos, shiftingManager.sampleResolution() << 3, null), executorService)
                            );
                        }
                    }
                }
            }
        }
    }

    private static Supplier<SingleScreenTileLayer> submitTileFuture(TileCoordinateShiftingManager shiftingManager, DataTileManager dataTileManager, TileLayer.GenerationFactory<?> generationFactory, TileLayer.DiskFactory diskFactory, String name, AtomicBoolean changesDetected, int tileSize, long tilePos, int sampleResolution, @Nullable SingleScreenTileLayer lastResolution) {
        return () -> {
            var worldMinTileX = shiftingManager.getWorldXFromTileKey(tilePos);
            var worldMinTileZ = shiftingManager.getWorldZFromTileKey(tilePos);

            String levelName = dataTileManager.serverLevel().getServer().getWorldData().getLevelName();

            Path imagePath = ModPlatform.INSTANCE.configPath().resolve("client").resolve("map").resolve(levelName).resolve(name).resolve("image").resolve("p." + shiftingManager.blockToTile(worldMinTileX) + "-" + shiftingManager.blockToTile(worldMinTileZ) + "_s." + tileSize + ".png");
            Path dataPath = ModPlatform.INSTANCE.configPath().resolve("client").resolve("map").resolve(levelName).resolve(name).resolve("data").resolve("p." + shiftingManager.blockToTile(worldMinTileX) + "-" + shiftingManager.blockToTile(worldMinTileZ) + "_s." + tileSize + ".dat");
            LongSet sampledChunks = new LongOpenHashSet();

            TileLayer tileLayer = getTileLayer(shiftingManager, dataTileManager, tileSize, sampleResolution, lastResolution, diskFactory, imagePath, dataPath, generationFactory, worldMinTileX, worldMinTileZ, sampledChunks);

            SingleScreenTileLayer tile = new SingleScreenTileLayer(tileLayer, worldMinTileX, worldMinTileZ, tileSize);
            changesDetected.set(true);

            sampledChunks.forEach(dataTileManager::unloadTile);
            return tile;
        };
    }

    private static TileLayer getTileLayer(TileCoordinateShiftingManager shiftingManager, DataTileManager dataTileManager, int tileSize, int sampleResolution, @Nullable SingleScreenTileLayer lastResolution, TileLayer.DiskFactory diskFactory, Path imagePath, Path dataPath, TileLayer.GenerationFactory generationFactory, int x, int z, LongSet sampledChunks) {
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
            tileLayer = generateTile(dataTileManager, generationFactory, 63, x, z, tileSize, sampleResolution, dataPath, imagePath, sampledChunks, tileLayer);
        } else {
            if (!tileLayer.isComplete()) {
                tileLayer.close();
                tileLayer = generateTile(dataTileManager, generationFactory, 63, x, z, tileSize, sampleResolution, dataPath, imagePath, sampledChunks, null);
            } else {
                boolean resolutionsDontMatch = tileLayer.sampleRes() != shiftingManager.sampleResolution();
                boolean usesLod = tileLayer.usesLod();
                if (usesLod && resolutionsDontMatch) {
                    tileLayer.close();
                    tileLayer = generateTile(dataTileManager, generationFactory, 63, x, z, tileSize, tileLayer.sampleRes() >> 1, dataPath, imagePath, sampledChunks, tileLayer);
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

    private static <T extends TileLayer> T generateTile(DataTileManager dataTileManager, TileLayer.GenerationFactory<T> generationFactory, int scrollY, int minTileWorldX, int minTileWorldZ, int size, int sampleRes, Path dataPath, Path imagePath, LongSet sampledChunks, @Nullable T lowerResolution) {
        if (sampleRes < 1) {
            throw new IllegalArgumentException("Sample resolution must at least 1 to generate a tile layer.");
        }
        T tileLayer1 = generationFactory.make(dataTileManager, scrollY, minTileWorldX, minTileWorldZ, size, sampleRes, sampledChunks, lowerResolution);
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

        int[] image = tileLayer1.image();
        if (image != null) {
            FILE_SAVING_EXECUTOR_SERVICE.submit(() -> {
                try {
                    int[] imageByteArray = image;
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

//                    Files.write(imagePath, imageByteArray);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
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
        }
    }

    @Override
    public void close() {

        for (WVDynamicTexture wvDynamicTexture : this.texturesToRender) {
            wvDynamicTexture.close();
        }
        this.executorService.shutdownNow();

        this.dataTileManager.close();
    }

    public void onScroll(int delta) {
        // Run on Render Thread.
        Minecraft.getInstance().submit(() -> {
            this.executorService.shutdownNow();
            for (Long2ObjectLinkedOpenHashMap<CompletableFuture<SingleScreenTileLayer>> futures : this.trackedTileLayerFutures) {
                futures.clear();
            }
            this.executorService = createExecutor("Screen-Tile-Generator-IO");
        });


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
