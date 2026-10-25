package com.realistictrees.event;

import com.realistictrees.ModEntities;
import com.realistictrees.TreeConfig;
import com.realistictrees.entity.CustomTreeEntity;
import com.realistictrees.entity.TreeSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Заменяет ванильные деревья (ВСЕ породы, включая модовые) на CustomTreeEntity.
 * Чанки не сканируются сразу при загрузке, а ставятся в очередь и обрабатываются по несколько штук за тик.
 */
public class TreeReplacer {
    private record Task(ServerLevel level, long chunk, int retries) {}

    private static final ConcurrentLinkedQueue<Task> QUEUE = new ConcurrentLinkedQueue<>();

    private static final int MAX_TREE_BLOCKS = 6000;
    private static final int MAX_RADIUS = 18;
    private static final int MAX_HEIGHT = 60;

    public static void queue(ServerLevel level, ChunkPos pos) {
        QUEUE.add(new Task(level, pos.toLong(), 0));
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!TreeConfig.COMMON.replaceWorldgenTrees.get()) return;
        if (event.getLevel() instanceof ServerLevel level) {
            queue(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        QUEUE.clear();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) return;
        int budget = TreeConfig.COMMON.chunksPerTick.get();
        int batch = QUEUE.size();
        for (int i = 0; i < batch && budget > 0; i++) {
            Task t = QUEUE.poll();
            if (t == null) break;
            ChunkPos cp = new ChunkPos(t.chunk());
            LevelChunk chunk = t.level().getChunkSource().getChunkNow(cp.x, cp.z);
            if (chunk == null) {
                if (t.retries() < 100) QUEUE.add(new Task(t.level(), t.chunk(), t.retries() + 1));
                continue;
            }
            // ждём соседей, чтобы деревья на границе чанков заменялись целиком
            if (t.retries() < 40 && !neighborsLoaded(t.level(), cp)) {
                QUEUE.add(new Task(t.level(), t.chunk(), t.retries() + 1));
                continue;
            }
            scanChunk(t.level(), chunk, cp);
            budget--;
        }
    }

    private static boolean neighborsLoaded(ServerLevel level, ChunkPos cp) {
        return level.getChunkSource().getChunkNow(cp.x + 1, cp.z) != null
                && level.getChunkSource().getChunkNow(cp.x - 1, cp.z) != null
                && level.getChunkSource().getChunkNow(cp.x, cp.z + 1) != null
                && level.getChunkSource().getChunkNow(cp.x, cp.z - 1) != null;
    }

    // ---------------------------------------------------------------- scan

    public static boolean isWood(BlockState s) {
        return s.is(BlockTags.LOGS) || BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().equals("mangrove_roots");
    }

    private static boolean isLeaves(BlockState s) {
        return s.is(BlockTags.LEAVES);
    }

    private static void scanChunk(ServerLevel level, LevelChunk chunk, ChunkPos cp) {
        Set<Long> visited = new HashSet<>();
        int minX = cp.getMinBlockX();
        int minZ = cp.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = minX + x;
                int wz = minZ + z;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;

                // в колонке сверху должна быть крона или ствол
                boolean treeTop = false;
                for (int dy = 0; dy <= 1; dy++) {
                    BlockState st = chunk.getBlockState(m.set(wx, top - dy, wz));
                    if (isLeaves(st) || isWood(st)) { treeTop = true; break; }
                }
                if (!treeTop) continue;

                int yEnd = Math.max(minY + 1, top - 56);
                for (int y = top; y >= yEnd; y--) {
                    BlockState st = chunk.getBlockState(m.set(wx, y, wz));
                    if (!isWood(st)) continue;
                    BlockState below = chunk.getBlockState(m.set(wx, y - 1, wz));
                    if (isWood(below) || below.isAir() || isLeaves(below)) continue;
                    if (visited.contains(BlockPos.asLong(wx, y, wz))) break;
                    tryReplace(level, new BlockPos(wx, y, wz), visited);
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- flood fill

    private static BlockState stateIfLoaded(ServerLevel level, BlockPos p) {
        return level.hasChunkAt(p) ? level.getBlockState(p) : null;
    }

    private static int leafDistance(BlockState s) {
        return s.hasProperty(LeavesBlock.DISTANCE) ? s.getValue(LeavesBlock.DISTANCE) : -1;
    }

    private static void tryReplace(ServerLevel level, BlockPos start, Set<Long> chunkVisited) {
        BlockState startState = level.getBlockState(start);
        Block logBlock = startState.getBlock();

        Map<Long, Integer> seen = new HashMap<>();   // pos -> расстояние (0 = бревно)
        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> leaves = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<Long, Boolean> isLog = new HashMap<>();

        seen.put(start.asLong(), 0);
        isLog.put(start.asLong(), true);
        queue.add(start);
        logs.add(start);
        int natural = 0;

        while (!queue.isEmpty() && seen.size() < MAX_TREE_BLOCKS) {
            BlockPos p = queue.poll();
            long key = p.asLong();
            int d = seen.get(key);
            boolean log = isLog.get(key);

            if (log) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos n = p.offset(dx, dy, dz);
                            if (!inBounds(start, n)) continue;
                            long nk = n.asLong();
                            if (seen.containsKey(nk)) continue;
                            BlockState ns = stateIfLoaded(level, n);
                            if (ns == null) continue;
                            if (isWood(ns) && ns.getBlock() == logBlock) {
                                seen.put(nk, 0); isLog.put(nk, true); logs.add(n); queue.add(n);
                            } else if (isLeaves(ns) && (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) == 1) {
                                int ld = leafDistance(ns);
                                if (ld == -1 || ld == 1) {
                                    seen.put(nk, 1); isLog.put(nk, false); leaves.add(n); queue.add(n);
                                    if (isNaturalLeaf(ns)) natural++;
                                }
                            }
                        }
                    }
                }
            } else {
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                    BlockPos n = p.relative(dir);
                    if (!inBounds(start, n)) continue;
                    long nk = n.asLong();
                    if (seen.containsKey(nk)) continue;
                    BlockState ns = stateIfLoaded(level, n);
                    if (ns == null || !isLeaves(ns)) continue;
                    int ld = leafDistance(ns);
                    // идём только "от ствола": расстояние листа растёт на 1
                    if ((ld == -1 && d < 7) || ld == d + 1) {
                        seen.put(nk, d + 1); isLog.put(nk, false); leaves.add(n); queue.add(n);
                        if (isNaturalLeaf(ns)) natural++;
                    }
                }
            }
        }

        for (BlockPos l : logs) chunkVisited.add(l.asLong());

        if (natural < TreeConfig.COMMON.minLeaves.get()) return;   // не дерево (постройка игрока и т.п.)

        // --- параметры дерева ---
        int baseY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos p : logs) { baseY = Math.min(baseY, p.getY()); maxY = Math.max(maxY, p.getY()); }
        for (BlockPos p : leaves) maxY = Math.max(maxY, p.getY());
        double sx = 0, sz = 0;
        int baseLogs = 0;
        for (BlockPos p : logs) {
            if (p.getY() == baseY) { sx += p.getX() + 0.5; sz += p.getZ() + 0.5; baseLogs++; }
        }
        double cx = sx / baseLogs;
        double cz = sz / baseLogs;
        int treeHeight = maxY - baseY + 1;

        ResourceLocation logId = BuiltInRegistries.BLOCK.getKey(logBlock);
        String wood = TreeSpecies.woodOf(logId);
        int model = TreeSpecies.pickModel(wood, baseLogs, treeHeight, level.random);
        float scale = TreeSpecies.scaleFor(model, treeHeight) * TreeConfig.COMMON.treeScale.get().floatValue();

        CustomTreeEntity tree = new CustomTreeEntity(ModEntities.CUSTOM_TREE.get(), level);
        tree.setPos(cx, baseY - 0.05, cz);
        tree.setYRot(level.random.nextFloat() * 360f);
        tree.setTreeType(model);
        tree.setTreeScale(scale);
        tree.setLogItem(logId.toString());
        tree.setLogCount(logs.size());
        if (!level.addFreshEntity(tree)) return;

        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos p : logs) level.setBlock(p, air, flags);
        for (BlockPos p : leaves) level.setBlock(p, air, flags);
    }

    private static boolean isNaturalLeaf(BlockState s) {
        return !s.hasProperty(LeavesBlock.PERSISTENT) || !s.getValue(LeavesBlock.PERSISTENT);
    }

    private static boolean inBounds(BlockPos origin, BlockPos p) {
        return Math.abs(p.getX() - origin.getX()) <= MAX_RADIUS
                && Math.abs(p.getZ() - origin.getZ()) <= MAX_RADIUS
                && p.getY() >= origin.getY() - 4
                && p.getY() <= origin.getY() + MAX_HEIGHT;
    }

    // нужен для /tree replace: сканирует уже загруженные чанки вокруг точки
    public static int queueAround(ServerLevel level, ChunkPos center, int radius) {
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                queue(level, new ChunkPos(center.x + dx, center.z + dz));
                n++;
            }
        }
        return n;
    }
}
