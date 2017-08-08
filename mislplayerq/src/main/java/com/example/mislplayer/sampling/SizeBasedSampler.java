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
     * <p>A sample will be finished each time this many bytes have been
     * transferred.
     *
     * @param sampleStore The store to send the throughput samples to.
     * @param sampleThresholdBytes The threshold for throughput sampling.
     */
    public SizeBasedSampler(SampleStore sampleStore,
                            long sampleThresholdBytes) {
        this.sampleThresholdBytes = sampleThresholdBytes;
        this.sampleStore = sampleStore;
    }

    private static final long DEFAULT_SAMPLE_THRESHOLD = 20_000;

    private SampleStore sampleStore;

    private long sampleThresholdBytes;

    private long byteClock = 0;
    private long sampleStartMs = 0;
    private long sampleDurationMs = 0;

    /**
     * Called when a transfer starts.
     *
     * @param source   The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        if (startedSampling()) {
            resumeSampling();
        } else {
            startSampling();
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
        updateSample(bytesTransferred);
    }

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    @Override
    public void onTransferEnd(Object source) {
        pauseSampling();
    }

    /**
     * Begin a throughput sample.
     */
    private void startSampling() {
        sampleStartMs = SystemClock.elapsedRealtime();
        byteClock = 0;
        sampleDurationMs = 0;
    }

    /**
     * Finish a throughput sample and send it to the sample store.
     */
    private void finishSampling() {
        long nowMs = SystemClock.elapsedRealtime();
        sampleDurationMs += nowMs - sampleStartMs;
        sampleStore.addSample(byteClock * 8, sampleDurationMs);
        sampleStartMs = 0;
    }

    /**
     * Pause sampling, to be resumed later.
     */
    private void pauseSampling() {
        long nowMs = SystemClock.elapsedRealtime();
        sampleDurationMs += nowMs - sampleStartMs;
    }

    /**
     * Resume sampling after pausing it.
     */
    private void resumeSampling() {
        sampleStartMs = SystemClock.elapsedRealtime();
    }

    /**
     * Update the current sample.
     *
     * @param bytesTransferred The number of bytes transferred since the
     *                         last update.
     */
    private void updateSample(int bytesTransferred) {
        byteClock += bytesTransferred;

        if (sampleIsReady()) {
            finishSampling();
            startSampling();
        }
    }

    /**
     * Indicates whether the sample has overcome the threshold.
     *
     * @return true if the sample is ready, false otherwise.
     */
    private boolean sampleIsReady() {
        return byteClock >= sampleThresholdBytes;
    }

    /**
     * Indicates whether there is an ongoing sample.
     *
     * @return true if we are in the middle of sampling, false otherwise.
     */
    private boolean startedSampling() {
        return sampleStartMs != 0;
    }
}
