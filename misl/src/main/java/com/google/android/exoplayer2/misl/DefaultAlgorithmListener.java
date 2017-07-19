package com.google.android.exoplayer2.misl;

import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import static com.google.android.exoplayer2.ExoPlayer.STATE_BUFFERING;
import static com.google.android.exoplayer2.ExoPlayer.STATE_READY;

/**
 * Collects information and provides it to an {@link AdaptationAlgorithm}.
 */
public class DefaultAlgorithmListener implements AlgorithmListener {

    private final static String TAG = "DefaultAL";

    private MediaChunk lastChunk;
    private long transferClockMs = DATA_NOT_AVAILABLE;
    private long lastLoadDurationMs = DATA_NOT_AVAILABLE;
    private long lastArrivalTime = DATA_NOT_AVAILABLE;
    private long stallDurationMs = DATA_NOT_AVAILABLE;
    private long stallStartMs = DATA_NOT_AVAILABLE;

    /**
     * The index of the last segment in the stream.
     *
     * @return The last segment's index.
     */
    @Override
    public int lastSegmentNumber() {
        return lastChunk == null ? DATA_NOT_AVAILABLE : lastChunk.chunkIndex;
    }

    /**
     * The value of {@link SystemClock#elapsedRealtime()} when the
     * last segment finished downloading.
     *
     * @return The last downloaded segment's arrival time.
     */
    @Override
    public long lastArrivalTime() {
        return lastArrivalTime;
    }

    /**
     * The length of time it took to load the last segment, in ms.
     *
     * @return The last segment's load duration in ms.
     */
    @Override
    public long lastLoadDurationMs() {
        return lastLoadDurationMs;
    }

    /**
     * The length of time the player has stalled for since starting the
     * video, in ms.
     *
     * @return The player's stall duration in ms.
     */
    @Override
    public long stallDurationMs() {
        return 0;
    }

    /**
     * The representation rate of the last segment, in bits per second.
     *
     * @return The last segment's representation rate in bits per second.
     */
    @Override
    public double lastRepresentationRate() {
        return lastChunk == null ? DATA_NOT_AVAILABLE : lastChunk.trackFormat.bitrate;
    }

    /**
     * The delivery rate of the last segment, in bits per second.
     *
     * @return The last segment's delivery rate in bits per second.
     */
    @Override
    public double lastDeliveryRate() {
        return lastChunk == null ? DATA_NOT_AVAILABLE : (lastChunk.bytesLoaded() * 8E3 / lastLoadDurationMs);
    }

    /**
     * The actual data rate of the last segment, in bits per second.
     *
     * @return The last segment's actual rate in bits per second.
     */
    @Override
    public double lastActualRate() {
        return lastChunk == null ? DATA_NOT_AVAILABLE : (lastChunk.bytesLoaded() * 1E6 / lastChunk.getDurationUs());
    }

    /**
     * The size of the last segment, in bytes.
     *
     * @return The last segment's size in bytes.
     */
    @Override
    public long lastByteSize() {
        return lastChunk == null ? DATA_NOT_AVAILABLE : lastChunk.bytesLoaded();
    }

    /**
     * Gives the listener the last chunk that was downloaded, to be passed to the
     * adaptation algorithm.
     *
     * @param lastChunk The last chunk that was downloaded.
     */
    @Override
    public void giveLastChunk(MediaChunk lastChunk) {
        if (lastChunk != null) {
            this.lastChunk = lastChunk;
        }
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
        long nowMs = SystemClock.elapsedRealtime();

        lastLoadDurationMs = nowMs - transferClockMs;
        lastArrivalTime = nowMs;
        Log.d(TAG, "Transfer ended");
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
        if (playbackState == STATE_BUFFERING && stallStartMs == DATA_NOT_AVAILABLE) {
            stallStartMs = SystemClock.elapsedRealtime();
            Log.d(TAG, "Stall started.");
        } else if (playbackState == STATE_READY && stallStartMs != DATA_NOT_AVAILABLE) {
            long nowMs = SystemClock.elapsedRealtime();
            stallDurationMs = nowMs - stallStartMs;
            stallStartMs = DATA_NOT_AVAILABLE;
            Log.d(TAG, String.format("Stall of duration %d ms finished", stallDurationMs));
        }
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
