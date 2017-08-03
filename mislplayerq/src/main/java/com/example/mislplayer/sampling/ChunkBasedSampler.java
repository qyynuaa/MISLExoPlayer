package com.example.mislplayer.sampling;

import android.os.SystemClock;
import android.util.Log;

import com.example.mislplayer.ChunkListener;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Samples the available throughput on a chunk-by-chunk basis.
 */
public class ChunkBasedSampler implements TransferListener<Object>, ChunkListener {

    /**
     * Creates a chunk-based sampler.
     *
     * @param sampleStore The store to send throughput samples to.
     * @param chunkStore The store to send downloaded chunks to.
     */
    public ChunkBasedSampler(SampleStore sampleStore, ChunkStore chunkStore) {
        this.sampleStore = sampleStore;
        this.chunkStore = chunkStore;
    }

    private static final String TAG = "ChunkBasedSampler";

    private SampleStore sampleStore;
    private ChunkStore chunkStore;
    private MediaChunk lastChunk;

    private long transferClockMs;
    private long loadDurationMs;

    /**
     * Gives the listener the last chunk that was downloaded, to be passed to the
     * adaptation algorithm.
     *
     * @param lastChunk The last chunk that was downloaded.
     */
    @Override
    public void giveLastChunk(MediaChunk lastChunk) {
        if (lastChunk == null) {
            Log.d(TAG, "null chunk received");
            return;
        } else if (lastChunk == this.lastChunk) {
            Log.d(TAG, "duplicate chunk received");
            return;
        }
        Log.d(TAG, "non-null chunk received:");

        long chunkSizeBits = lastChunk.bytesLoaded() * 8;

        sampleStore.addSample(chunkSizeBits, loadDurationMs);
        chunkStore.add(lastChunk);
        this.lastChunk = lastChunk;
    }

    /**
     * Gives the listener the duration of the mpd.
     *
     * @param durationMs The duration of the mpd in ms.
     */
    @Override
    public void giveMpdDuration(long durationMs) {
        chunkStore.giveMpdDuration(durationMs);
    }

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
        long arrivalTimeMs = SystemClock.elapsedRealtime();
        loadDurationMs = arrivalTimeMs - transferClockMs;
    }
}
