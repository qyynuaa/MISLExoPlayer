package com.example.mislplayer.sampling;

import android.os.SystemClock;

import com.example.mislplayer.ChunkListener;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Samples the available throughput on a chunk-by-chunk basis.
 *
 * <p>A sample should be delivered every time a chunk finishes, and only
 * then. However, this depends on {@link #giveLastChunk} being called at
 * the correct time (after a chunk has been downloaded and before
 * updateTrackSelection() is called for downloading the next chunk.
 */
public class ChunkBasedSampler implements TransferListener<Object>, ChunkListener {

    private static final String TAG = "ChunkBasedSampler";

    private SampleStore sampleStore;
    private SampleProcessor sampleProcessor;
    private MediaChunk lastChunk;

    private long transferClockMs;
    private long loadDurationMs;
    private long elapsedRealtimeMs;

    /**
     * Creates a chunk-based sampler.
     *
     *  @param sampleStore The store to send throughput samples to.
     * @param sampleProcessor The processor to send chunks to.
     */
    public ChunkBasedSampler(SampleStore sampleStore, SampleProcessor sampleProcessor) {
        this.sampleStore = sampleStore;
        this.sampleProcessor = sampleProcessor;
    }

    // ChunkListener implementation

    @Override
    public void giveLastChunk(MediaChunk lastChunk) {
        if (lastChunk == null) {
            return;
        } else if (lastChunk == this.lastChunk) {
            return;
        }

        long chunkSizeBits = lastChunk.bytesLoaded() * 8;

        sampleStore.addSample(elapsedRealtimeMs, chunkSizeBits, loadDurationMs);
        sampleProcessor.giveChunk(lastChunk);
        this.lastChunk = lastChunk;
    }

    // TransferListener implementation

    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        transferClockMs = SystemClock.elapsedRealtime();
    }

    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {}

    @Override
    public void onTransferEnd(Object source) {
        elapsedRealtimeMs = SystemClock.elapsedRealtime();
        loadDurationMs = elapsedRealtimeMs - transferClockMs;
    }
}
