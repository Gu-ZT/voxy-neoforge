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
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

//TODO: Add a render cache


//TODO: to add remove functionallity add a "defunked" variable to the build task and set it to true on remove
// and process accordingly
public class RenderGenerationService {
    private static final int MAX_HOLDING_SECTION_COUNT = 1000;
    private static final int DISTANCE_BUCKET_BITS = 20;
    private static final long DISTANCE_BUCKET_MASK = (1L << DISTANCE_BUCKET_BITS) - 1L;

    public static final AtomicInteger MESH_FAILED_COUNTER = new AtomicInteger();
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final class BuildTask {
        WorldSection section;
        final long position;
        boolean hasDoneModelRequestInner;
        boolean hasDoneModelRequestOuter;
        int attempts;
        int addin;
        long priority = Long.MIN_VALUE;
        private BuildTask(long position) {
            this.position = position;
        }
        private void updatePriority(int originX, int originY, int originZ) {
            int unique = COUNTER.incrementAndGet();
            long distanceBucket = computeDistanceBucket(this.position, originX, originY, originZ);
            // Distance-first priority so near rings converge before far updates.
            // Tie-breaks: higher-detail LODs first, then retry/adin bookkeeping.
            int lodLevel = Math.min(7, Math.max(0, WorldEngine.getLevel(this.position)));
            long retryClass = (Math.min(this.attempts, 3) * 2L) + this.addin;
            long tieBreaker = ((long) lodLevel << 3) | retryClass;
            this.priority = (((distanceBucket << 6) | tieBreaker) << 32) + Integer.toUnsignedLong(unique);
            this.addin = 0;
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

    private final Service service;
    private volatile int priorityOriginSectionX;
    private volatile int priorityOriginSectionY;
    private volatile int priorityOriginSectionZ;


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
        }, 10, "Section mesh generation service", ()->{
            int modelBakeQueueCount = modelBakery.getProcessingCount();
            if (modelBakeQueueCount>1000) return false;//Pause mesh gen if there is alot of model baking happening
            return modelBakery.getProcessingCount()<400||RenderGenerationService.MESH_FAILED_COUNTER.get()<500;
        });
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

    private static long computeDistanceBucket(long pos, int originX, int originY, int originZ) {
        int lvl = WorldEngine.getLevel(pos);
        int x = WorldEngine.getX(pos);
        int y = WorldEngine.getY(pos);
        int z = WorldEngine.getZ(pos);

        int scale = 1 << lvl;
        int centerX = (x << lvl) + (scale >> 1);
        int centerY = (y << lvl) + (scale >> 1);
        int centerZ = (z << lvl) + (scale >> 1);

        long dx = (long) centerX - originX;
        long dy = (long) centerY - originY;
        long dz = (long) centerZ - originZ;

        // Prioritize a near-player "ring" expansion: horizontal distance dominates, vertical distance is de-emphasized.
        long horizontalDistSq = dx * dx + dz * dz;
        long verticalPenalty = (dy * dy) >> 2;
        long distance = horizontalDistSq + verticalPenalty;
        return Math.min(DISTANCE_BUCKET_MASK, distance);
    }

    public void setPriorityOrigin(double cameraX, double cameraY, double cameraZ) {
        this.priorityOriginSectionX = ((int) Math.floor(cameraX)) >> 5;
        this.priorityOriginSectionY = ((int) Math.floor(cameraY)) >> 5;
        this.priorityOriginSectionZ = ((int) Math.floor(cameraZ)) >> 5;
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
            {
                long stamp = this.taskMapLock.writeLock();
                BuildTask other = this.taskMap.putIfAbsent(task.position, task);
                this.taskMapLock.unlockWrite(stamp);

                if (other != null) {//Weve been replaced
                    //Request the block
                    if (e.isIdBlockId) {
                        //TODO: maybe move this to _after_ task as been readded to queue??
                        if (!this.modelBakery.factory.hasModelForBlockId(e.id)) {
                            if (seenMissedIds.add(e.id)) {
                                this.modelBakery.requestBlockBake(e.id);
                            }
                        }
                    }
                    //Exchange info
                    if (task.hasDoneModelRequestInner) {
                        other.hasDoneModelRequestInner = true;
                    }
                    if (task.hasDoneModelRequestOuter) {
                        other.hasDoneModelRequestOuter = true;
                    }
                    if (task.section != null) {
                        this.holdingSectionCount.decrementAndGet();
                    }
                    task.section = null;
                    shouldFreeSection = true;
                    task = null;
                }
            }
            if (task != null) {
                //This is our task

                //Request the block
                if (e.isIdBlockId) {
                    //TODO: maybe move this to _after_ task as been readded to queue??
                    if (!this.modelBakery.factory.hasModelForBlockId(e.id)) {
                        if (seenMissedIds.add(e.id)) {
                            this.modelBakery.requestBlockBake(e.id);
                        }
                    }
                }

                if (task.hasDoneModelRequestOuter || task.hasDoneModelRequestInner) {
                    MESH_FAILED_COUNTER.incrementAndGet();
                }

                if (task.hasDoneModelRequestInner && task.hasDoneModelRequestOuter) {
                    task.attempts++;
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                } else {
                    if (task.hasDoneModelRequestInner) {
                        task.attempts++;//This is because it can be baking and just model thing isnt keeping up
                    }

                    if (!task.hasDoneModelRequestInner) {
                        //The reason for the extra id parameter is that we explicitly add/check against the exception id due to e.g. requesting accross a chunk boarder wont be captured in the request
                        if (e.auxData == null)//the null check this is because for it to be, the inner must already be computed
                            this.computeAndRequestRequiredModels(seenMissedIds, section);
                        task.hasDoneModelRequestInner = true;
                    }
                    //If this happens... aahaha painnnn
                    if (task.hasDoneModelRequestOuter) {
                        task.attempts++;
                    }

                    if ((!task.hasDoneModelRequestOuter) && e.auxData != null) {
                        this.computeAndRequestRequiredModels(seenMissedIds, e.auxBitMsk, e.auxData);
                        task.hasDoneModelRequestOuter = true;
                    }

                    task.addin = WorldEngine.getLevel(task.position)>2?1:0;//Single time addin which gives the models time to bake before the task executes
                }

                //Keep the lock on the section, and attach it to the task, this prevents needing to re-aquire it later
                if (task.section == null) {
                    if (this.holdingSectionCount.get() < MAX_HOLDING_SECTION_COUNT) {
                        this.holdingSectionCount.incrementAndGet();
                        task.section = section;
                        shouldFreeSection = false;
                    }
                } else {
                    shouldFreeSection = false;
                }

                task.updatePriority(this.priorityOriginSectionX, this.priorityOriginSectionY, this.priorityOriginSectionZ);
                this.taskQueue.add(task);
                this.taskQueueCount.incrementAndGet();

                if (this.service.isLive()) {//Only execute if were not dead
                    this.service.execute();//Since we put in queue, release permit
                }
            }
        }

        if (shouldFreeSection) {
            if (task != null && task.section != null) {
                this.holdingSectionCount.decrementAndGet();
            }
            section.release();
        }

        if (mesh != null) {//If the mesh is null it means it didnt finish, so dont submit
            if (this.resultConsumer != null) {
                this.resultConsumer.accept(mesh);
            } else {
                mesh.free();
            }
        }
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
            task.updatePriority(this.priorityOriginSectionX, this.priorityOriginSectionY, this.priorityOriginSectionZ);
            this.taskQueue.add(task);
            this.taskQueueCount.incrementAndGet();
            this.service.execute();
        }
    }

    /*
    public void enqueueTask(int lvl, int x, int y, int z) {
        this.enqueueTask(WorldEngine.getWorldSectionId(lvl, x, y, z));
    }
    */

    public void shutdown() {
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
