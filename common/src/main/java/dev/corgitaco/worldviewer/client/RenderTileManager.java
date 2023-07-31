package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import corgitaco.corgilib.platform.ModPlatform;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.RenderTile;
import dev.corgitaco.worldviewer.client.tile.RenderTileOfTiles;
import dev.corgitaco.worldviewer.client.tile.ScreenTile;
import dev.corgitaco.worldviewer.client.tile.TileCoordinateShiftingManager;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.util.LongPackingUtil;
import io.netty.util.internal.ConcurrentSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class RenderTileManager {
    private ExecutorService executorService = createExecutor();

    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<RenderTile>> trackedTileFutures = new Long2ObjectLinkedOpenHashMap<>();
    private WorldScreenv2 worldScreenv2;
    private final ServerLevel level;
    private final BlockPos origin;

    public final Map<Long, RenderTile> loaded = new ConcurrentHashMap<>();
    public final Map<Integer, Map<Long, ScreenTile>> toRender = new ConcurrentHashMap<>();

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
        for (ScreenTile tileToRender : renderTiles) {

            int localX = (int) worldScreenv2.getLocalXFromWorldX(tileToRender.getTileWorldX());
            int localZ = (int) worldScreenv2.getLocalZFromWorldZ(tileToRender.getTileWorldZ());

            int screenTileMinX = (worldScreenv2.getScreenCenterX() + localX);
            int screenTileMinZ = (worldScreenv2.getScreenCenterZ() + localZ);

            poseStack.pushPose();
            poseStack.translate(screenTileMinX, screenTileMinZ, 0);
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));

            tileToRender.renderTile(graphics);

            poseStack.popPose();
        }
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
        trackedTileFutures.forEach((tilePos, future) -> {
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
                    }
                });
            }
        });

        toRemove.forEach(this.trackedTileFutures::remove);

        toClose.removeIf(renderTile -> {
//            renderTile.close(false);
            return true;
        });

        this.loaded.forEach((tilePos, renderTile) -> {
            int tileX = worldScreenv2.shiftingManager.getTileX(tilePos);
            int tileZ = worldScreenv2.shiftingManager.getTileZ(tilePos);

            int scale = 4;
            int getNextScaleMinTileX = (tileX / scale) * scale;
            int getNextScaleMaxTileX = (getNextScaleMinTileX + scale);

            int getNextScaleMinTileZ = (tileZ / scale) * scale;
            int getNextScaleMaxTileZ = (getNextScaleMinTileZ + scale);
            long minTileKey = LongPackingUtil.tileKey(getNextScaleMinTileX, getNextScaleMinTileZ);

            if (!toRender.computeIfAbsent(scale, key1 -> new Long2ObjectLinkedOpenHashMap<>()).containsKey(minTileKey)) {
                int xRange = Math.abs(getNextScaleMaxTileX - getNextScaleMinTileX);
                int zRange = Math.abs(getNextScaleMaxTileZ - getNextScaleMinTileZ);
                ScreenTile[][] tilesToRender = new ScreenTile[xRange][zRange];
                for (int tileOffsetX = 0; tileOffsetX < xRange; tileOffsetX++) {
                    for (int tileOffsetZ = 0; tileOffsetZ < zRange; tileOffsetZ++) {
                        long tileKey = LongPackingUtil.tileKey(getNextScaleMinTileX + tileOffsetX, getNextScaleMinTileZ + tileOffsetZ);
                        RenderTile offset = loaded.get(tileKey);
                        if (offset != null && offset.getSampleRes() == worldScreenv2.sampleResolution) {
                            tilesToRender[tileOffsetX][tileOffsetZ] = offset;
                        } else {
                            return;
                        }
                    }
                }

                toRender.computeIfAbsent(scale, key1 -> new Long2ObjectLinkedOpenHashMap<>()).put(minTileKey, new RenderTileOfTiles(tilesToRender));
            }

        });

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

            RenderTile renderTile = new RenderTile(this.tileManager, TileLayer.FACTORY_REGISTRY, 63, x, z, tileSize, sampleResolution, worldScreenv2, lastResolution);
            RenderTile previous = loaded.put(tilePos, renderTile);
            if (previous != null && previous != renderTile) {
                this.toClose.add(previous);
            }
            return renderTile;
        }, executorService));
    }

    public void cull(WorldScreenv2 worldScreenv2) {
        LongSet toRemove = new LongOpenHashSet();
        this.loaded.forEach((pos, tile) -> {
            int x = tile.getTileWorldX();
            int z = tile.getTileWorldZ();
            if (!worldScreenv2.worldViewArea.intersects(x, z, x, z)) {
//                tile.close(true);
                toRemove.add(pos);
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
