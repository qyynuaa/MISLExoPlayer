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
 * Samples the available throughput every x ms.
 */
public class TimeBasedSampler implements TransferListener<Object>,
        ExoPlayer.EventListener {

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

    private static final int TIME_UNSET = -1;

    private SampleStore sampleStore;

    private long sampleThresholdMs;

    private long sampleBytesTransferred = 0;
    private long sampleClockMs = TIME_UNSET;
    private long sampleDurationMs = 0;

    /**
     * Called when a transfer starts.
     *
     * @param source   The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        if (currentlySampling()) {
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

    }

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
            sampleStore.addSample(sampleBytesTransferred * 8, sampleDurationMs);
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
        return sampleDurationMs >= sampleThresholdMs;
    }

    /**
     * Indicates whether there is an ongoing sample.
     *
     * @return true if we are in the middle of sampling, false otherwise.
     */
    private boolean currentlySampling() {
        return sampleClockMs != TIME_UNSET;
    }

    /**
     * Called when the timeline and/or manifest has been refreshed.
     * <p>
     * Note that if the timeline has changed then a position discontinuity may also have occurred.
     * For example, the current period index may have changed as a result of periods being added or
     * removed from the timeline. This will <em>not</em> be reported via a separate call to
     * {@link #onPositionDiscontinuity()}.
     *
     * @param timeline The latest timeline. Never null, but may be empty.
     * @param manifest The latest manifest. May be null.
     */
    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    /**
     * Called when the available or selected tracks change.
     *
     * @param trackGroups     The available tracks. Never null, but may be of length zero.
     * @param trackSelections The track selections for each {@link Renderer}. Never null and always
     *                        of length {@link ExoPlayer#getRendererCount()}, but may contain null elements.
     */
    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    /**
     * Called when the player starts or stops loading the source.
     *
     * @param isLoading Whether the source is currently being loaded.
     */
    @Override
    public void onLoadingChanged(boolean isLoading) {
        if (!isLoading && currentlySampling()) {
            finishSampling();
            Log.d(TAG, "Loading changed, finished premature sample.");
        }
    }

    /**
     * Called when the value returned from either {@link ExoPlayer#getPlayWhenReady()} or
     * {@link ExoPlayer#getPlaybackState()} changes.
     *
     * @param playWhenReady Whether playback will proceed when ready.
     * @param playbackState One of the {@code STATE} constants defined in the {@link ExoPlayer}
     */
    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

    }

    /**
     * Called when an error occurs. The playback state will transition to {@link ExoPlayer#STATE_IDLE}
     * immediately after this method is called. The player instance can still be used, and
     * {@link ExoPlayer#release()} must still be called on the player should it no longer be required.
     *
     * @param error The error.
     */
    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    /**
     * Called when a position discontinuity occurs without a change to the timeline. A position
     * discontinuity occurs when the current window or period index changes (as a result of playback
     * transitioning from one period in the timeline to the next), or when the playback position
     * jumps within the period currently being played (as a result of a seek being performed, or
     * when the source introduces a discontinuity internally).
     * <p>
     * When a position discontinuity occurs as a result of a change to the timeline this method is
     * <em>not</em> called. {@link #onTimelineChanged(Timeline, Object)} is called in this case.
     */
    @Override
    public void onPositionDiscontinuity() {

    }

    /**
     * Called when the current playback parameters change. The playback parameters may change due to
     * a call to {@link ExoPlayer#setPlaybackParameters(PlaybackParameters)}, or the player itself
     * may change them (for example, if audio playback switches to passthrough mode, where speed
     * adjustment is no longer possible).
     *
     * @param playbackParameters The playback parameters.
     */
    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }
}
