package me.cortex.voxy.client.core.model;


import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.other.Mapper;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

public class ModelBakerySubsystem {
    //Redo to just make it request the block faces with the async texture download stream which
    // basicly solves all the render stutter due to the baking

    private final ModelStore storage = new ModelStore();
    public final ModelFactory factory;
    private final Mapper mapper;
    private final AtomicInteger blockIdCount = new AtomicInteger();
    private final ConcurrentLinkedDeque<Integer> blockIdQueue = new ConcurrentLinkedDeque<>();//TODO: replace with custom DS

    private final Thread processingThread;
    private volatile boolean isRunning = true;
    public ModelBakerySubsystem(Mapper mapper) {
        this.mapper = mapper;
        this.factory = new ModelFactory(mapper, this.storage);
        this.processingThread = new Thread(()->{//TODO replace this with something good/integrate it into the async processor so that we just have less threads overall
            while (this.isRunning) {
                this.factory.processAllThings();
                // Sleep less when there's a large backlog (initial world load with many block states).
                // Drops to 1ms during heavy load so baked textures are available sooner,
                // reducing IdNotYetComputedException retries in RenderGenerationService.
                int sleepMs = this.blockIdCount.get() > 20 ? 1 : 10;
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, "Model factory processor");
        this.processingThread.start();
    }

    public void tick(long totalBudget) {
        long start = System.nanoTime();
        this.factory.tickAndProcessUploads();
        //Always do 1 iteration minimum
        Integer i = this.blockIdQueue.poll();
        if (i != null) {
            int j = 0;
            if (i != null) {
                int fbBinding = glGetInteger(GL_FRAMEBUFFER_BINDING);

                do {
                    this.factory.addEntry(i);
                    j++;
                    // Process at least 16 blocks per tick unconditionally, then respect budget.
                    // 50_000ns (50µs) of lookahead prevents stopping right at the budget boundary.
                    // Higher minimum reduces startup time for large modpacks (was 4, now 16).
                    if (16 < j && (totalBudget < (System.nanoTime() - start) + 50_000))
                        break;
                    i = this.blockIdQueue.poll();
                } while (i != null);

                glBindFramebuffer(GL_FRAMEBUFFER, fbBinding);//This is done here as stops needing to set then unset the fb in the thing 1000x
            }
            this.blockIdCount.addAndGet(-j);
        }

        //TimingStatistics.modelProcess.stop();
    }

    public void shutdown() {
        this.isRunning = false;
        try {
            this.processingThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        this.factory.free();
        this.storage.free();
    }

    // Lock-free set of block IDs that have been seen (queued or baked).
    // ConcurrentHashMap.newKeySet() gives lock-free add/contains/remove without serializing
    // all worker threads on a single ReentrantLock during initial load with thousands of IDs.
    private final Set<Integer> seenIds = ConcurrentHashMap.newKeySet(6000);
    public void requestBlockBake(int blockId) {
        if (this.mapper.getBlockStateCount() < blockId) {
            Logger.error("Error, got bakeing request for out of range state id. StateId: " + blockId + " max id: " + this.mapper.getBlockStateCount(), new Exception());
            return;
        }
        // Fast path: already baked
        if (this.factory.hasModelForBlockId(blockId)) {
            return;
        }
        boolean isNew = this.seenIds.add(blockId);
        if (!isNew) {
            // Was seen before. If the model still hasn't been baked (idMappings == -1),
            // the previous bake was either deferred (fluid dependency not ready) or stuck.
            // Re-queue via addEntry which will handle dedup via blockStatesInFlight.
            // We only do this once by removing from seenIds so the next call can re-add it.
            if (!this.factory.hasModelForBlockId(blockId)) {
                this.seenIds.remove(blockId); // Allow future re-queue if needed
                this.blockIdQueue.add(blockId);
                this.blockIdCount.incrementAndGet();
            }
            return;
        }
        this.blockIdQueue.add(blockId);
        this.blockIdCount.incrementAndGet();
    }

    public void addBiome(Mapper.BiomeEntry biomeEntry) {
        this.factory.addBiome(biomeEntry);
    }

    public void addDebugData(List<String> debug) {
        debug.add(String.format("MQ/IF/MC: %04d, %03d, %04d", this.blockIdCount.get(), this.factory.getInflightCount(),  this.factory.getBakedCount()));//Model bake queue/in flight/model baked count
    }

    public ModelStore getStore() {
        return this.storage;
    }

    public boolean areQueuesEmpty() {
        return this.blockIdCount.get()==0 && this.factory.getInflightCount() == 0;
    }

    public int getProcessingCount() {
        return this.blockIdCount.get() + this.factory.getInflightCount();
    }
}
