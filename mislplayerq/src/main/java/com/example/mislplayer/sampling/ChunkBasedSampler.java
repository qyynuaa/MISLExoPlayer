package com.example.mislplayer.sampling;

import android.os.SystemClock;

import com.example.mislplayer.ChunkListener;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Samples the available throughput on a chunk-by-chunk basis.
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
     *  @param sampleStore The store to send throughput samples to.
     * @param sampleProcessor The processor to send chunks to.
     */
    public ChunkBasedSampler(SampleStore sampleStore, SampleProcessor sampleProcessor) {
        this.sampleStore = sampleStore;
        this.sampleProcessor = sampleProcessor;
    }

    // ChunkListener implementation

    /**
     * Gives the listener the last chunk that was downloaded, to be passed to the
     * adaptation algorithm.
     *
     * @param lastChunk The last chunk that was downloaded.
     */
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

    /**
     * Called when a transfer starts.
     *
     * @param source   The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        transferClockMs = SystemClock.elapsedRealtime();
    }

    /**
     * Called incrementally during a transfer.
     *
     * @param source           The source performing the transfer.
     * @param bytesTransferred The number of bytes transferred since the previous call to this
     */
    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {

    }

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    @Override
    public void onTransferEnd(Object source) {
        elapsedRealtimeMs = SystemClock.elapsedRealtime();
        loadDurationMs = elapsedRealtimeMs - transferClockMs;
    }
}
