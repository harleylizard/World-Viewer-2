package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import corgitaco.corgilib.platform.ModPlatform;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.RenderTile;
import dev.corgitaco.worldviewer.client.tile.RenderTileOfTiles;
import dev.corgitaco.worldviewer.client.tile.ScreenTile;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.util.LongPackingUtil;
import io.netty.util.internal.ConcurrentSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class RenderTileManager {
    private ExecutorService executorService = createExecutor();

    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<RenderTile>> trackedTileFutures = new Long2ObjectLinkedOpenHashMap<>();
    private WorldScreenv2 worldScreenv2;
    private final ServerLevel level;
    private final BlockPos origin;

    public final Long2ObjectOpenHashMap<RenderTile> loaded = new Long2ObjectOpenHashMap<>();
    public final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<ScreenTile>> toRender = new Int2ObjectOpenHashMap<>();

    public final ConcurrentSet<RenderTile> toClose = new ConcurrentSet<>();


    private final DataTileManager tileManager;


    public boolean blockGeneration = true;

    public RenderTileManager(WorldScreenv2 worldScreenv2, ServerLevel level, BlockPos origin) {
        this.worldScreenv2 = worldScreenv2;
        this.level = level;
        this.origin = origin;
        tileManager = new DataTileManager(ModPlatform.PLATFORM.configDir().resolve(String.valueOf(level.getSeed())), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());
        long originTile = worldScreenv2.shiftingManager.tileKey(origin);
        loadTiles(worldScreenv2, originTile);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, WorldScreenv2 worldScreenv2) {

        this.toRender.forEach((scale, tiles) -> {
            renderTiles(guiGraphics, this.worldScreenv2, tiles.values());
        });
    }

    private static void renderTiles(GuiGraphics graphics, WorldScreenv2 worldScreenv2, Collection<? extends ScreenTile> renderTiles) {
        PoseStack poseStack = graphics.pose();
        renderTiles.forEach(tileToRender -> {
            if (tileToRender != null) {
                int localX = (int) worldScreenv2.getLocalXFromWorldX(tileToRender.getMinTileWorldX());
                int localZ = (int) worldScreenv2.getLocalZFromWorldZ(tileToRender.getMinTileWorldZ());

                int screenTileMinX = (worldScreenv2.getScreenCenterX() + localX);
                int screenTileMinZ = (worldScreenv2.getScreenCenterZ() + localZ);

                poseStack.pushPose();
                poseStack.translate(screenTileMinX, screenTileMinZ, 0);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));

                tileToRender.renderTile(graphics, worldScreenv2.scale);

                poseStack.popPose();
            }
        });
    }


    public DataTileManager getDataTileManager() {
        return tileManager;
    }

    public void tick() {
        long originTile = worldScreenv2.shiftingManager.tileKey(this.origin);
        if (!blockGeneration) {
            loadTiles(worldScreenv2, originTile);
            blockGeneration = true;
        }


        LongSet toRemove = new LongOpenHashSet();

        List<Runnable> toSubmit = new ArrayList<>();
        trackedTileFutures.long2ObjectEntrySet().fastForEach(entry -> {
            long tilePos = entry.getLongKey();
            CompletableFuture<RenderTile> future = entry.getValue();
            if (future.isCompletedExceptionally()) {
                try {
                    future.getNow(null);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            if (future.isDone()) {
                toRemove.add(tilePos);
            }

            int worldX = worldScreenv2.shiftingManager.getWorldXFromTileKey(tilePos);
            int worldZ = worldScreenv2.shiftingManager.getWorldZFromTileKey(tilePos);
            if (!worldScreenv2.worldViewArea.intersects(worldX, worldZ, worldX, worldZ) && !future.isCancelled()) {
                future.cancel(true);
                toRemove.add(tilePos);
            } else {
                toSubmit.add(() -> {
                    RenderTile renderTile = future.getNow(null);
                    if (renderTile != null) {
                        int newSampleRes = renderTile.getSampleRes() >> 1;
                        if (newSampleRes >= worldScreenv2.sampleResolution) {
                            submitTileFuture(worldScreenv2, renderTile.getSize(), tilePos, newSampleRes, renderTile);
                        }

                        RenderTile previous = loaded.put(tilePos, renderTile);
                        this.toRender.computeIfAbsent(1, key1 -> new Long2ObjectOpenHashMap<>()).put(tilePos, renderTile);
                        if (previous != null && previous != renderTile) {
                            this.toClose.add(previous);
                        }
                    }
                });
            }
        });

        toRemove.forEach(this.trackedTileFutures::remove);

        toClose.removeIf(renderTile -> {
//            renderTile.close(false);
            return true;
        });


        List<Runnable> toRun = new ArrayList<>();
        this.toRender.int2ObjectEntrySet().fastForEach((entry) -> {
            int currentScale = entry.getIntKey();
            Long2ObjectOpenHashMap<ScreenTile> tiles = entry.getValue();

            int newScale = currentScale << 1;


            tiles.long2ObjectEntrySet().fastForEach(screenTileEntry -> {
                long tilePos = screenTileEntry.getLongKey();
                ScreenTile screenTile = screenTileEntry.getValue();

                int tileX = worldScreenv2.shiftingManager.getTileX(tilePos);
                int tileZ = worldScreenv2.shiftingManager.getTileZ(tilePos);

                int getNextScaleMinTileX = (tileX / newScale) * newScale;
                int getNextScaleMaxTileX = (getNextScaleMinTileX + newScale);

                int getNextScaleMinTileZ = (tileZ / newScale) * newScale;
                int getNextScaleMaxTileZ = (getNextScaleMinTileZ + newScale);

                long minTileKey = LongPackingUtil.tileKey(getNextScaleMinTileX, getNextScaleMinTileZ);

                if (!toRender.computeIfAbsent(newScale, key1 -> new Long2ObjectOpenHashMap<>()).containsKey(minTileKey)) {
                    int xRange = Math.abs(getNextScaleMaxTileX - getNextScaleMinTileX);
                    int zRange = Math.abs(getNextScaleMaxTileZ - getNextScaleMinTileZ);

                    int xTileIncrement = xRange / 2;
                    int zTileIncrement = zRange / 2;

                    ScreenTile[][] tilesToRender = new ScreenTile[2][2];

                    long[] positions = new long[2 * 2];
                    for (int tileOffsetX = 0; tileOffsetX < 2; tileOffsetX++) {
                        for (int tileOffsetZ = 0; tileOffsetZ < 2; tileOffsetZ++) {
                            int tileX1 = getNextScaleMinTileX + (tileOffsetX * xTileIncrement);
                            int tileZ1 = getNextScaleMinTileZ + (tileOffsetZ * zTileIncrement);
                            long tileKey = LongPackingUtil.tileKey(tileX1, tileZ1);
                            positions[tileOffsetX * 2 + tileOffsetZ] = tileKey;

                            ScreenTile offset = this.toRender.get(currentScale).get(tileKey);

                            if (offset != null && offset.sampleResCheck(worldScreenv2.sampleResolution)) {
                                tilesToRender[tileOffsetX][tileOffsetZ] = offset;
                            } else {
                                return;
                            }
                        }
                    }
                    toRun.add(() -> {
                        toRender.computeIfAbsent(newScale, key1 -> new Long2ObjectOpenHashMap<>()).put(minTileKey, new RenderTileOfTiles(tilesToRender, newScale));
                        for (long pos : positions) {
                            toRender.get(currentScale).remove(pos);
                        }
                    });
                }
            });
        });
        toRun.forEach(Runnable::run);


        toSubmit.forEach(Runnable::run);
    }

    private void loadTiles(WorldScreenv2 worldScreenv2, long originTile) {
        int xTileRange = worldScreenv2.getXTileRange();
        int zTileRange = worldScreenv2.getZTileRange();

        int slices = 360;
        double sliceSize = Mth.TWO_PI / slices;

        int tileRange = Math.max(xTileRange, zTileRange) + 2;
        for (int tileDistanceFromOrigin = 0; tileDistanceFromOrigin <= tileRange; tileDistanceFromOrigin++) {

            int tileSize = worldScreenv2.tileSize;
            int originWorldX = worldScreenv2.shiftingManager.getWorldXFromTileKey(originTile) + (tileSize / 2);
            int originWorldZ = worldScreenv2.shiftingManager.getWorldZFromTileKey(originTile) + (tileSize / 2);
            double distance = tileSize * tileDistanceFromOrigin;

            for (int i = 0; i < slices; i++) {
                double angle = i * sliceSize;
                int worldTileX = (int) Math.round(originWorldX + (Math.sin(angle) * distance));
                int worldTileZ = (int) Math.round(originWorldZ + (Math.cos(angle) * distance));
                if (worldScreenv2.worldViewArea.intersects(worldTileX, worldTileZ, worldTileX, worldTileZ)) {
                    long tilePos = LongPackingUtil.tileKey(worldScreenv2.shiftingManager.blockToTile(worldTileX), worldScreenv2.shiftingManager.blockToTile(worldTileZ));
                    RenderTile tile = loaded.get(tilePos);
                    if (tile == null) {
                        submitTileFuture(worldScreenv2, tileSize, tilePos, worldScreenv2.sampleResolution << 3, null);
                    }
                }
            }
        }
    }

    private void submitTileFuture(WorldScreenv2 worldScreenv2, int tileSize, long tilePos, int sampleResolution, @Nullable RenderTile lastResolution) {
        trackedTileFutures.computeIfAbsent(tilePos, key -> CompletableFuture.supplyAsync(() -> {
            var x = worldScreenv2.shiftingManager.getWorldXFromTileKey(tilePos);
            var z = worldScreenv2.shiftingManager.getWorldZFromTileKey(tilePos);

            RenderTile tile = new RenderTile(this.tileManager, TileLayer.FACTORY_REGISTRY, 63, x, z, tileSize, sampleResolution, worldScreenv2, lastResolution);
            return tile;
        }, executorService));
    }

    public void cull(WorldScreenv2 worldScreenv2) {
        LongSet toRemove = new LongOpenHashSet();
        this.loaded.forEach((pos, tile) -> {
            int minTileWorldX = tile.getMinTileWorldX();
            int minTileWorldZ = tile.getMinTileWorldZ();
            int maxTileWorldX = tile.getMaxTileWorldX();
            int maxTileWorldZ = tile.getMaxTileWorldZ();
            if (!worldScreenv2.worldViewArea.intersects(minTileWorldX, minTileWorldZ, maxTileWorldX, maxTileWorldZ)) {
                toRemove.add(pos);
            }
        });


        Int2ObjectOpenHashMap<LongList> toRemoveRender = new Int2ObjectOpenHashMap<>();

        this.toRender.int2ObjectEntrySet().fastForEach(entry -> {
            int scale = entry.getIntKey();
            Long2ObjectOpenHashMap<ScreenTile> tiles = entry.getValue();
            LongList longs = toRemoveRender.computeIfAbsent(scale, key -> new LongArrayList());

            tiles.long2ObjectEntrySet().fastForEach(screenTileEntry -> {
                long tilePos = screenTileEntry.getLongKey();
                ScreenTile tile = screenTileEntry.getValue();

                int minTileWorldX = tile.getMinTileWorldX();
                int minTileWorldZ = tile.getMinTileWorldZ();
                int maxTileWorldX = tile.getMaxTileWorldX();
                int maxTileWorldZ = tile.getMaxTileWorldZ();
                if (!worldScreenv2.worldViewArea.intersects(minTileWorldX, minTileWorldZ, maxTileWorldX, maxTileWorldZ)) {
                    tile.close();
                    longs.add(tilePos);
                }
            });
        });

        toRemoveRender.int2ObjectEntrySet().fastForEach(longListEntry -> {
            int scale = longListEntry.getIntKey();
            LongList tiles = longListEntry.getValue();
            Long2ObjectOpenHashMap<ScreenTile> longScreenTileMap = this.toRender.get(scale);
            tiles.forEach(key -> {
                ScreenTile remove = longScreenTileMap.remove(key);
                if (remove != null) {
                    remove.close();
                }
            });

            if (longScreenTileMap.isEmpty()) {
                this.toRender.remove(scale);
            }
        });

        toRemove.forEach(loaded::remove);
    }

    public void close() {
        this.executorService.shutdownNow();
//        this.rendering.forEach((pos, tile) -> tile.close(true));
        this.loaded.clear();
        this.tileManager.close();
    }

    public void onScroll() {
        this.executorService.shutdownNow();
        this.executorService = createExecutor();
        this.trackedTileFutures.clear();
//        this.rendering.forEach((pos, tile) -> tile.close(true));
        this.loaded.clear();
    }

    public static ExecutorService createExecutor() {
        return createExecutor(Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 25));
    }

    public static ExecutorService createExecutor(int processors) {
        return Executors.newFixedThreadPool(processors, new ThreadFactory() {
            private final ThreadFactory backing = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(@NotNull Runnable r) {
                var thread = backing.newThread(r);
                thread.setDaemon(true);
                return thread;
            }
        });
    }
}
