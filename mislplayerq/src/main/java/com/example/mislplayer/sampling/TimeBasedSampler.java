package com.example.mislplayer.sampling;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Samples the available throughput every x ms while a chunk is downloading.
 */
public class TimeBasedSampler implements TransferListener<Object> {

    private static final String TAG = "TimeBasedSampler";

    /**
     * Constructs a time-based sampler with a default sampling threshold.
     *
     * @param sampleStore The store to send the throughput samples to.
     */
    public TimeBasedSampler(SampleStore sampleStore) {
        this(sampleStore, DEFAULT_SAMPLE_THRESHOLD);
    }

    /**
     * Constructs a time-based sampler by specifying a sampling threshold.
     *
     * <p>A sample will be finished each time this many ms have elapsed.
     *
     * @param sampleStore The store to send the throughput samples to.
     * @param sampleThresholdMs The threshold for throughput sampling.
     */
    public TimeBasedSampler(SampleStore sampleStore,
                            long sampleThresholdMs) {
        this.sampleThresholdMs = sampleThresholdMs;
        this.sampleStore = sampleStore;
    }

    private static final long DEFAULT_SAMPLE_THRESHOLD = 500;
    private static final long TIME_UNSET = -1;

    private SampleStore sampleStore;

    private Handler sampleHandler = new Handler();
    private Runnable sampleRunnable = new Runnable() {
        @Override
        public void run() {
            deliverTimeSample();

            sampleHandler.postDelayed(sampleRunnable, sampleThresholdMs);
        }
    };

    private long sampleThresholdMs;

    private long sampleBytesTransferred;
    private long sampleStartMs;

    private long chunkBytesTransferred;
    private long chunkStartMs;

    /**
     * Called when a transfer starts.
     *
     * @param source   The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        sampleHandler.postDelayed(sampleRunnable, sampleThresholdMs);

        chunkStartMs = SystemClock.elapsedRealtime();
        chunkBytesTransferred = 0;

        sampleStartMs = chunkStartMs;
        sampleBytesTransferred = 0;
    }

    /**
     * Called incrementally during a transfer.
     *
     * @param source           The source performing the transfer.
     * @param bytesTransferred The number of bytes transferred since the previous call to this
     */
    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {
        this.chunkBytesTransferred += bytesTransferred;
        this.sampleBytesTransferred += bytesTransferred;
    }

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    @Override
    public void onTransferEnd(Object source) {
        sampleHandler.removeCallbacks(sampleRunnable);
        deliverChunkSample();
    }

    /**
     * Finish a time-based throughput sample and send it to the sample
     * store.
     */
    private void deliverTimeSample() {
        long nowMs = SystemClock.elapsedRealtime();
        long sampleDurationMs = nowMs - sampleStartMs;

        if (sampleDurationMs > 0) {
            sampleStore.addSample(sampleBytesTransferred * 8, sampleDurationMs);
            sampleStartMs = nowMs;
            sampleBytesTransferred = 0;
            Log.d(TAG, "Time sample delivered.");
        }
    }

    /**
     * Finish a chunk-based throughput sample and send it to the sample
     * store.
     */
    private void deliverChunkSample() {
        long nowMs = SystemClock.elapsedRealtime();
        long sampleDurationMs = nowMs - chunkStartMs;

        if (sampleDurationMs > 0) {
            sampleStore.addSample(chunkBytesTransferred * 8, sampleDurationMs);
            chunkStartMs = TIME_UNSET;
            chunkBytesTransferred = 0;
            Log.d(TAG, "Chunk sample delivered.");
        }
    }
}
