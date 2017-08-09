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
 * Samples the throughput in one of a number of modes, and can be switched
 * between those modes.
 */
public class SwitchableSampler implements TransferListener<Object>,
        ExoPlayer.EventListener {

    public enum SampleMode {
        /**
         * Time-based sampling.
         *
         * <p>Once a threshold of time spent downloading has been exceeded,
         * a sample is finished.
         */
        TIME {
            @Override
            public boolean sampleIsReady(long durationMs,
                                         long bytesTransferred,
                                         long thresholdMs,
                                         long thresholdBytes) {
                return durationMs >= thresholdMs;
            }
        },
        /**
         * Size-based sampling.
         *
         * <p>Once a threshold of bytes downloaded has been exceeded, a
         * sample is finished.
         */
        SIZE {
            @Override
            public boolean sampleIsReady(long durationMs,
                                         long bytesTransferred,
                                         long thresholdMs,
                                         long thresholdBytes) {
                return bytesTransferred >= thresholdBytes;
            }
        },
        /**
         * Uses size-based and time-based sampling, and finishes a sample
         * according to whichever threshold is exceeded first.
         */
        SIZE_OR_TIME {
            @Override
            public boolean sampleIsReady(long durationMs,
                                         long bytesTransferred,
                                         long thresholdMs,
                                         long thresholdBytes) {
                return bytesTransferred >= thresholdBytes
                        || durationMs >= thresholdMs;
            }
        },
        /**
         * Uses size-based and time-based sampling, and finished a sample
         * according to whichever threshold is exceeded last.
         */
        SIZE_AND_TIME {
            @Override
            public boolean sampleIsReady(long durationMs,
                                         long bytesTransferred,
                                         long thresholdMs,
                                         long thresholdBytes) {
                return bytesTransferred >= thresholdBytes
                        && durationMs >= thresholdMs;
            }
        };

        /**
         * Reports whether a sample is finished (ready to be delivered to
         * the sample store).
         *
         * @param durationMs The amount of time spent downloading during
         *                   the sample period, in ms.
         * @param bytesTransferred The number of bytes transferred during
         *                         the sample period.
         * @param thresholdMs The time-threshold for the sample, in ms.
         * @param thresholdBytes The size-threshold for the sample, in bytes.
         * @return true if the sample is finished, false otherwise
         */
        public abstract boolean sampleIsReady(long durationMs,
                                              long bytesTransferred,
                                              long thresholdMs,
                                              long thresholdBytes);
    }

    private static final String TAG = "SwitchableSampler";

    private static final long DEFAULT_THRESHOLD_MS = 500;
    private static final long DEFAULT_THRESHOLD_BYTES = 100_000;

    private static final int TIME_UNSET = -1;

    private SampleStore sampleStore;
    private SampleMode mode;
    private long sampleThresholdMs;
    private long sampleThresholdBytes;

    private long sampleBytesTransferred = 0;
    private long sampleClockMs = TIME_UNSET;
    private long sampleDurationMs = 0;

    /**
     * Creates a switchable sampler with specified mode and default
     * threshold values.
     *
     * @param sampleStore The store to deliver throughput samples to.
     * @param mode The sampling mode to use.
     */
    public SwitchableSampler(SampleStore sampleStore, SampleMode mode) {
        this(sampleStore, mode, DEFAULT_THRESHOLD_MS,
                DEFAULT_THRESHOLD_BYTES);
    }

    /**
     * Creates a switchable sampler with specified mode and threshold.
     *
     * <p>The threshold determines when samples are considered "ready",
     * e.g. a threshold of 10 kB will mean any sample of 10 kB or more will
     * be considered ready.
     *
     * <p>For time-based estimation, the threshold is in ms. For size-based
     * estimation, it's in bytes.
     *
     * @param sampleStore The store to deliver throughput samples to.
     * @param mode The sampling mode to use.
     * @param sampleThreshold The sampling threshold to use.
     */
    public SwitchableSampler(SampleStore sampleStore, SampleMode mode,
                             long sampleThreshold) {
                this(sampleStore, mode,
                        mode == SampleMode.TIME ? sampleThreshold : DEFAULT_THRESHOLD_MS,
                        mode == SampleMode.SIZE ? sampleThreshold : DEFAULT_THRESHOLD_BYTES);
    }

    /**
     * Creates a switchable sampler with specified mode and thresholds.
     *
     * <p>Depending on the mode, the thresholds may be ignored.
     *
     * @param sampleStore The store to deliver throughput samples to.
     * @param mode The sampling mode to use.
     * @param sampleThresholdMs The time threshold to use (in ms).
     * @param sampleThresholdBytes The size threshold to use (in bytes).
     */
    public SwitchableSampler(SampleStore sampleStore, SampleMode mode,
                             long sampleThresholdMs,
                             long sampleThresholdBytes) {
        this.sampleStore = sampleStore;
        this.mode = mode;
        this.sampleThresholdMs = sampleThresholdMs;
        this.sampleThresholdBytes = sampleThresholdBytes;
    }

    /** Change to a new sampling mode. */
    public void changeMode(SampleMode mode) {
        this.mode = mode;
    }

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
        Log.d(TAG, "Transfer ended");
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

        if (mode.sampleIsReady(sampleDurationMs, sampleBytesTransferred,
                sampleThresholdMs, sampleThresholdBytes)) {
            finishSampling();
            startSampling();
        }
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
