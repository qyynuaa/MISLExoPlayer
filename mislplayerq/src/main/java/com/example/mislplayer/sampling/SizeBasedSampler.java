package com.example.mislplayer.sampling;

import android.os.SystemClock;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Samples the available throughput every x bytes.
 */
public class SizeBasedSampler implements TransferListener<Object> {

    /**
     * Constructs a size-based sampler with a default sampling threshold.
     *
     * @param sampleStore The store to send the throughput samples to.
     */
    public SizeBasedSampler(SampleStore sampleStore) {
        this(sampleStore, DEFAULT_SAMPLE_THRESHOLD);
    }

    /**
     * Constructs a size-based sampler by specifying a sampling threshold.
     *
     * @param sampleStore The store to send the throughput samples to.
     * @param sampleThreshold The threshold for threshold sampling. Sampling
     *                        will occur when at least this many bytes
     *                        have been transferred since the last sample.
     */
    public SizeBasedSampler(SampleStore sampleStore,
                            long sampleThreshold) {
        sampleThresholdBits = sampleThreshold;
        this.sampleStore = sampleStore;
    }

    private static final long DEFAULT_SAMPLE_THRESHOLD = 20_000;

    private SampleStore sampleStore;

    private long sampleThresholdBits;
    private long byteClock = 0;
    private long sampleStartMs;

    /**
     * Called when a transfer starts.
     *
     * @param source   The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        if (byteClock == 0) {
            sampleStartMs = SystemClock.elapsedRealtime();
        }
    }

    /**
     * Called incrementally during a transfer.
     *
     * @param source           The source performing the transfer.
     * @param bytesTransferred The number of bytes transferred since the previous call to this
     */
    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {
        byteClock += bytesTransferred;

        if (byteClock > sampleThresholdBits) {
            long nowMs = SystemClock.elapsedRealtime();
            sampleStore.addSample(bytesTransferred * 8,
                    nowMs - sampleStartMs);

            sampleStartMs = nowMs;
            byteClock = 0;
        }
    }

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    @Override
    public void onTransferEnd(Object source) {

    }
}