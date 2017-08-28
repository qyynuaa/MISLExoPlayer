package com.example.mislplayer.sampling;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.example.mislplayer.ChunkListener;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Samples the available throughput every x ms while a chunk is downloading.
 */
public class TimeBasedSampler implements TransferListener<Object>,
        SampleProcessor.Receiver, ChunkListener {

    private static final String TAG = "TimeBasedSampler";

    private static final long DEFAULT_SAMPLE_THRESHOLD = 4000;

    private SampleProcessor.Receiver sampleReceiver;
    private ChunkBasedSampler chunkSampler;

    private Handler sampleHandler = new Handler();
    private Runnable sampleRunnable = new Runnable() {
        @Override
        public void run() {
            deliverCompleteTimeSample();

            sampleHandler.postDelayed(sampleRunnable, sampleThresholdMs);
        }
    };

    private long sampleThresholdMs;

    private long sampleBytesTransferred;
    private long sampleStartMs;
    private long transferStartMs;
    private long transferEndMs;

    /**
     * Constructs a time-based sampler with a default sampling threshold.
     *
     * @param sampleReceiver The receiver for throughput samples and
     *                        chunks.
     */
    public TimeBasedSampler(SampleProcessor.Receiver sampleReceiver) {
        this(sampleReceiver, DEFAULT_SAMPLE_THRESHOLD);
    }

    /**
     * Constructs a time-based sampler by specifying a sampling threshold.
     *
     *  @param sampleReceiver The receiver for throughput samples and
     *                        chunks.
     * @param sampleThresholdMs The threshold for throughput sampling.
     */
    public TimeBasedSampler(SampleProcessor.Receiver sampleReceiver,
                            long sampleThresholdMs) {
        this.sampleThresholdMs = sampleThresholdMs;
        this.sampleReceiver = sampleReceiver;
        this.chunkSampler = new ChunkBasedSampler(this);
    }

    /**
     * Called when a transfer starts.
     *
     * @param source   The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        sampleHandler.postDelayed(sampleRunnable, sampleThresholdMs);

        transferStartMs = SystemClock.elapsedRealtime();
        sampleStartMs = transferStartMs;
        sampleBytesTransferred = 0;

        chunkSampler.onTransferStart(source, dataSpec);
    }

    /**
     * Called incrementally during a transfer.
     *
     * @param source           The source performing the transfer.
     * @param bytesTransferred The number of bytes transferred since the previous call to this
     */
    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {
        this.sampleBytesTransferred += bytesTransferred;

        chunkSampler.onBytesTransferred(source, bytesTransferred);
    }

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    @Override
    public void onTransferEnd(Object source) {
        transferEndMs = SystemClock.elapsedRealtime();
        sampleHandler.removeCallbacks(sampleRunnable);
        chunkSampler.onTransferEnd(source);
    }

    /**
     * Finish a time-based throughput sample and send it to the sample
     * store.
     */
    private void deliverTimeSample(long sampleEndTimeMs) {
        long sampleDurationMs = sampleEndTimeMs - sampleStartMs;

        if (sampleDurationMs > 0) {
            sampleReceiver.sendSample(sampleEndTimeMs, sampleBytesTransferred * 8, sampleDurationMs);
            sampleStartMs = sampleEndTimeMs;
            sampleBytesTransferred = 0;
            Log.d(TAG, "Time sample delivered.");
        }
    }

    private void deliverCompleteTimeSample() {
        deliverTimeSample(SystemClock.elapsedRealtime());
    }

    private void deliverPrematureTimeSample() {
        deliverTimeSample(transferEndMs);
    }

    private boolean inTimeBasedMode() {
        return transferStartMs != sampleStartMs;
    }

    // SampleProcessor.Receiver implementation

    /** Adds a new throughput sample to the store. */
    @Override
    public void sendSample(long elapsedRealtimeMs, long bitsTransferred, long durationMs) {
        if (inTimeBasedMode()) {
            deliverPrematureTimeSample();
        } else {
            sampleReceiver.sendSample(elapsedRealtimeMs, bitsTransferred, durationMs);
            Log.d(TAG, "Chunk sample delivered.");
        }
        sampleHandler.removeCallbacks(sampleRunnable);
    }

    @Override
    public void giveChunk(MediaChunk chunk) {
        sampleReceiver.giveChunk(chunk);
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
        chunkSampler.giveLastChunk(lastChunk);
    }
}
