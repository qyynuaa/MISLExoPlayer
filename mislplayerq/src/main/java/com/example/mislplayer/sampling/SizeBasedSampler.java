package com.example.mislplayer.sampling;

import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Samples the available throughput every x bytes.
 */
public class SizeBasedSampler implements TransferListener<Object>,
        ExoPlayer.EventListener {

    private static final String TAG = "SizeBasedSampler";

    /**
     * Creates a size-based sampler with a default sampling threshold.
     *
     * @param sampleReceiver The receiver for throughput samples.
     */
    public SizeBasedSampler(SampleProcessor.Receiver sampleReceiver) {
        this(sampleReceiver, DEFAULT_SAMPLE_THRESHOLD);
    }

    /**
     * Creates a size-based sampler with a specified sampling threshold.
     *
     * <p>A sample will be finished each time this many bytes have been
     * transferred.
     *
     * @param sampleReceiver The receiver for throughput samples.
     * @param sampleThresholdBytes The threshold for throughput sampling.
     */
    public SizeBasedSampler(SampleProcessor.Receiver sampleReceiver,
                            long sampleThresholdBytes) {
        this.sampleThresholdBytes = sampleThresholdBytes;
        this.sampleReceiver = sampleReceiver;
    }

    private static final long DEFAULT_SAMPLE_THRESHOLD = 100_000;

    private static final int TIME_UNSET = -1;

    private SampleProcessor.Receiver sampleReceiver;

    private long sampleThresholdBytes;

    private long sampleBytesTransferred = 0;
    private long sampleClockMs = TIME_UNSET;
    private long sampleDurationMs = 0;

    // TransferListener implementation

    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        if (currentlySampling()) {
            resumeSampling();
        } else {
            startSampling();
        }
    }

    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {
        updateSample(bytesTransferred);
    }

    @Override
    public void onTransferEnd(Object source) {}

    // Internal methods

    /**
     * Begin a throughput sample.
     */
    private void startSampling() {
        sampleClockMs = SystemClock.elapsedRealtime();
        sampleBytesTransferred = 0;
        sampleDurationMs = 0;
    }

    /**
     * Finish a throughput sample and send it to the sample store.
     */
    private void finishSampling() {
        if (sampleDurationMs > 0) {
            sampleReceiver.sendSample(SystemClock.elapsedRealtime(),
                    sampleBytesTransferred * 8, sampleDurationMs);
        }
        sampleClockMs = TIME_UNSET;
    }

    /**
     * Resume sampling.
     */
    private void resumeSampling() {
        sampleClockMs = SystemClock.elapsedRealtime();
    }

    /**
     * Update the current sample.
     *
     * @param bytesTransferred The number of bytes transferred since the
     *                         last update.
     */
    private void updateSample(int bytesTransferred) {
        long nowMs = SystemClock.elapsedRealtime();
        sampleDurationMs += nowMs - sampleClockMs;
        sampleClockMs = nowMs;
        sampleBytesTransferred += bytesTransferred;

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
        return sampleBytesTransferred >= sampleThresholdBytes;
    }

    /**
     * Indicates whether there is an ongoing sample.
     *
     * @return true if we are in the middle of sampling, false otherwise.
     */
    private boolean currentlySampling() {
        return sampleClockMs != TIME_UNSET;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {}

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

    @Override
    public void onLoadingChanged(boolean isLoading) {
        if (!isLoading && currentlySampling()) {
            finishSampling();
            Log.d(TAG, "Loading changed, finished premature sample.");
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {}

    @Override
    public void onPlayerError(ExoPlaybackException error) {}

    @Override
    public void onPositionDiscontinuity() {}

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}
}
