package me.cortex.voxy.client.core.rendering.building;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxy.client.core.model.IdNotYetComputedException;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

//TODO: Add a render cache


//TODO: to add remove functionallity add a "defunked" variable to the build task and set it to true on remove
// and process accordingly
public class RenderGenerationService {
    private static final int MAX_HOLDING_SECTION_COUNT = 1000;
    private static final int DEFERRED_RETRY_BASE_DELAY_MS = 250;

    public static final AtomicInteger MESH_FAILED_COUNTER = new AtomicInteger();
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private volatile int priorityOriginSectionX;
    private volatile int priorityOriginSectionY;
    private volatile int priorityOriginSectionZ;

    private final class BuildTask {
        WorldSection section;
        final long position;
        long priority = Long.MIN_VALUE;
        private BuildTask(long position) {
            this.position = position;
        }
        private void updatePriority() {
            int unique = COUNTER.incrementAndGet() & 0xFFFFFF;
            int lvlPriority = WorldEngine.MAX_LOD_LAYER-WorldEngine.getLevel(this.position);
            lvlPriority = Math.min(lvlPriority, 3);//Make the 2 highest quality have equal priority

            // Prioritize sections nearest to camera. This prevents initial ring processing from
            // filling a distant corner first when enqueue order is noisy.
            int lvl = WorldEngine.getLevel(this.position);
            int span = 1 << lvl;
            int sx = WorldEngine.getX(this.position) << lvl;
            int sy = WorldEngine.getY(this.position) << lvl;
            int sz = WorldEngine.getZ(this.position) << lvl;
            int ex = sx + span - 1;
            int ey = sy + span - 1;
            int ez = sz + span - 1;

            int ox = RenderGenerationService.this.priorityOriginSectionX;
            int oy = RenderGenerationService.this.priorityOriginSectionY;
            int oz = RenderGenerationService.this.priorityOriginSectionZ;

            int dx = ox < sx ? (sx - ox) : (ox > ex ? (ox - ex) : 0);
            int dy = oy < sy ? (sy - oy) : (oy > ey ? (oy - ey) : 0);
            int dz = oz < sz ? (sz - oz) : (oz > ez ? (oz - ez) : 0);
            long dist2 = (long)dx * dx + (long)dy * dy + (long)dz * dz;
            long distKey = Math.min(0xFFFFFFFFL, dist2);

            long classKey = (lvlPriority * 6L) & 0xFFL;
            this.priority = (classKey << 56) | (distKey << 24) | Integer.toUnsignedLong(unique);
        }
    }

    private final AtomicInteger holdingSectionCount = new AtomicInteger();//Used to limit section holding

    private final AtomicInteger taskQueueCount = new AtomicInteger();
    private final PriorityBlockingQueue<BuildTask> taskQueue = new PriorityBlockingQueue<>(5000, (a,b)-> Long.compareUnsigned(a.priority, b.priority));
    private final StampedLock taskMapLock = new StampedLock();
    private final Long2ObjectOpenHashMap<BuildTask> taskMap = new Long2ObjectOpenHashMap<>(5000);

    private final WorldEngine world;
    private final ModelBakerySubsystem modelBakery;
    private Consumer<BuiltSection> resultConsumer;
    private final boolean emitMeshlets;
    private final ConcurrentHashMap<Long, Integer> deferredRetryCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService deferredRetryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Voxy deferred mesh retry");
        t.setDaemon(true);
        return t;
    });

    private final Service service;


    /*
    public RenderGenerationService(WorldEngine world, ModelBakerySubsystem modelBakery, ServiceManager sm, boolean emitMeshlets) {
        this(world, modelBakery, sm, emitMeshlets, ()->true);
    }*/

    public RenderGenerationService(WorldEngine world, ModelBakerySubsystem modelBakery, ServiceManager sm, boolean emitMeshlets) {
        this.emitMeshlets = emitMeshlets;
        this.world = world;
        this.modelBakery = modelBakery;

        this.service = sm.createService(()->{
            //Thread local instance of the factory
            var factory = new RenderDataFactory(this.world, this.modelBakery.factory, this.emitMeshlets);
            IntOpenHashSet seenMissed = new IntOpenHashSet(128);
            return new Pair<>(() -> {
                this.processJob(factory, seenMissed);
            }, factory::free);
        }, 10, "Section mesh generation service", ()->modelBakery.getProcessingCount()<400||RenderGenerationService.MESH_FAILED_COUNTER.get()<500);
    }

    public void setResultConsumer(Consumer<BuiltSection> consumer) {
        this.resultConsumer = consumer;
    }

    //NOTE: the biomes are always fully populated/kept up to date

    //Asks the Model system to bake all blocks that currently dont have a model
    private void computeAndRequestRequiredModels(IntOpenHashSet seenMissedIds, int bitMsk, long[] auxData) {
        final var factory = this.modelBakery.factory;
        for (int i = 0; i < 6; i++) {
            if ((bitMsk&(1<<i))==0) continue;
            for (int j = 0; j < 32*32; j++) {
                int block = Mapper.getBlockId(auxData[j+(i*32*32)]);
                if (block != 0 && !factory.hasModelForBlockId(block)) {
                    if (seenMissedIds.add(block)) {
                        this.modelBakery.requestBlockBake(block);
                    }
                }
            }
        }
    }

    private void computeAndRequestRequiredModels(IntOpenHashSet seenMissedIds, WorldSection section) {
        //Know this is... very much not safe, however it reduces allocation rates and other garbage, am sure its "fine"
        final var factory = this.modelBakery.factory;
        for (long state : section._unsafeGetRawDataArray()) {
            int block = Mapper.getBlockId(state);
            if (block != 0 && !factory.hasModelForBlockId(block)) {
                if (seenMissedIds.add(block)) {
                    this.modelBakery.requestBlockBake(block);
                }
            }
        }
    }

    private WorldSection acquireSection(long pos) {
        return this.world.acquireIfExists(pos);
    }

    private static boolean putTaskFirst(long pos) {
        //Level 3 or 4
        return WorldEngine.getLevel(pos) > 2;
    }

    //TODO: add a generated render data cache
    private void processJob(RenderDataFactory factory, IntOpenHashSet seenMissedIds) {
        BuildTask task = this.taskQueue.poll();
        this.taskQueueCount.decrementAndGet();

        //long time = BuiltSection.getTime();
        boolean shouldFreeSection = true;

        WorldSection section;
        if (task.section == null) {
            section = this.acquireSection(task.position);
        } else {
            section = task.section;
        }


        {//Remove the task from the map, this is done before we check for null sections as well the task map needs to be correct
            long stamp = this.taskMapLock.writeLock();
            var rtask = this.taskMap.remove(task.position);
            if (rtask != task) {
                this.taskMapLock.unlockWrite(stamp);
                throw new IllegalStateException();
            }
            this.taskMapLock.unlockWrite(stamp);
        }

        if (section == null) {
            this.deferredRetryCounts.remove(task.position);
            if (this.resultConsumer != null) {
                this.resultConsumer.accept(BuiltSection.empty(task.position));
            }
            return;
        }
        section.assertNotFree();
        BuiltSection mesh = null;


        try {
            mesh = factory.generateMesh(section);
        } catch (IdNotYetComputedException e) {
            // Request missing models once, then fail fast for this section.
            // With air fallback active, retry loops mostly add CPU pressure and queue churn.
            if (e.isIdBlockId && !this.modelBakery.factory.hasModelForBlockId(e.id) && seenMissedIds.add(e.id)) {
                this.modelBakery.requestBlockBake(e.id);
            }
            if (e.auxData == null) {
                this.computeAndRequestRequiredModels(seenMissedIds, section);
            } else {
                this.computeAndRequestRequiredModels(seenMissedIds, e.auxBitMsk, e.auxData);
            }

            MESH_FAILED_COUNTER.incrementAndGet();
            if (this.resultConsumer != null) {
                this.resultConsumer.accept(BuiltSection.emptyWithChildren(task.position, section.getNonEmptyChildren()));
            }
            this.scheduleDeferredRetry(task.position);
            if (task.section != null) {
                this.holdingSectionCount.decrementAndGet();
            }
            section.release();
            return;
        }

        if (shouldFreeSection) {
            if (task != null && task.section != null) {
                this.holdingSectionCount.decrementAndGet();
            }
            section.release();
        }

        if (mesh != null) {//If the mesh is null it means it didnt finish, so dont submit
            this.deferredRetryCounts.remove(task.position);
            if (this.resultConsumer != null) {
                this.resultConsumer.accept(mesh);
            } else {
                mesh.free();
            }
        }
    }

    private void scheduleDeferredRetry(long pos) {
        int attempt = this.deferredRetryCounts.merge(pos, 1, Integer::sum);

        // Keep retrying at low frequency until success, so transient missing-model states
        // eventually refill gaps. Delay quickly ramps, then caps.
        long shift = Math.min(6, Math.max(0, attempt - 1));
        long delayMs = Math.min(10_000L, (long) DEFERRED_RETRY_BASE_DELAY_MS << shift);
        this.deferredRetryExecutor.schedule(() -> {
            if (!this.service.isLive()) {
                return;
            }
            this.enqueueTask(pos);
        }, delayMs, TimeUnit.MILLISECONDS);
    }


    public void enqueueTask(long pos) {
        if (!this.service.isLive()) {
            return;
        }
        boolean[] isOurs = new boolean[1];
        long stamp = this.taskMapLock.writeLock();
        BuildTask task = this.taskMap.computeIfAbsent(pos, p->{
                isOurs[0] = true;
                return new BuildTask(p);
            });
        this.taskMapLock.unlockWrite(stamp);

        if (isOurs[0]) {//If its not ours we dont care about it
            //Set priority and insert into queue and execute
            task.updatePriority();
            this.taskQueue.add(task);
            this.taskQueueCount.incrementAndGet();
            this.service.execute();
        }
    }

    public void setPriorityOrigin(double cameraX, double cameraY, double cameraZ) {
        this.priorityOriginSectionX = ((int) Math.floor(cameraX)) >> 5;
        this.priorityOriginSectionY = ((int) Math.floor(cameraY)) >> 5;
        this.priorityOriginSectionZ = ((int) Math.floor(cameraZ)) >> 5;
    }

    /*
    public void enqueueTask(int lvl, int x, int y, int z) {
        this.enqueueTask(WorldEngine.getWorldSectionId(lvl, x, y, z));
    }
    */

    public void shutdown() {
        this.deferredRetryExecutor.shutdownNow();
        this.deferredRetryCounts.clear();

        //Steal and free as much work as possible
        while (this.service.numJobs() != 0) {
            int i = this.service.drain();
            if (i == 0) break;
            {
                long stamp = this.taskMapLock.writeLock();
                for (int j = 0; j < i; j++) {
                    var task = this.taskQueue.remove();
                    if (task.section != null) {
                        task.section.release();
                        this.holdingSectionCount.decrementAndGet();
                    }
                    if (this.taskMap.remove(task.position) != task) {
                        throw new IllegalStateException();
                    }
                }
                this.taskMapLock.unlockWrite(stamp);
                this.taskQueueCount.addAndGet(-i);
            }
        }

        //Shutdown the threads
        this.service.shutdown();

        //Cleanup any remaining data
        while (!this.taskQueue.isEmpty()) {
            var task = this.taskQueue.remove();
            this.taskQueueCount.decrementAndGet();
            if (task.section != null) {
                task.section.release();
                this.holdingSectionCount.decrementAndGet();
            }

            long stamp = this.taskMapLock.writeLock();
            if (this.taskMap.remove(task.position) != task) {
                throw new IllegalStateException();
            }
            this.taskMapLock.unlockWrite(stamp);
        }
        if (this.taskQueueCount.get() != 0) {
            throw new IllegalStateException();
        }
    }

    private long lastChangedTime = 0;
    public void addDebugData(List<String> debug) {
        if (System.currentTimeMillis()-this.lastChangedTime > 100) {
            MESH_FAILED_COUNTER.set(0);
            this.lastChangedTime = System.currentTimeMillis();
        }
        debug.add("RSSQ/TFC: " + this.taskQueueCount.get() + "/" + MESH_FAILED_COUNTER.get());//render section service queue, Task Fail Counter

    }

    public int getTaskCount() {
        return this.taskQueueCount.get();
    }
}
