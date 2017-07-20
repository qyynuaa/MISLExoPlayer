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

import java.util.ArrayList;

import static com.google.android.exoplayer2.ExoPlayer.STATE_BUFFERING;
import static com.google.android.exoplayer2.ExoPlayer.STATE_READY;

/**
 * Collects information and provides it to an {@link AdaptationAlgorithm}.
 */
public class DefaultAlgorithmListener implements AlgorithmListener {

    private class AlgorithmData {
        public final MediaChunk chunk;
        public final long loadDurationMs;
        public final long arrivalTimeMs;

        private AlgorithmData(MediaChunk chunk, long loadDurationMs, long arrivalTimeMs) {
            this.chunk = chunk;
            this.loadDurationMs = loadDurationMs;
            this.arrivalTimeMs = arrivalTimeMs;
        }
    }

    private final static String TAG = "DefaultAL";

    private ArrayList<AlgorithmData> chunkData = new ArrayList<>();

    private MediaChunk lastChunk;
    private long lastLoadDurationMs;
    private long lastArrivalTimeMs;

    private MISLLoadControl loadControl;

    private long stallDurationMs = DATA_NOT_AVAILABLE;
    private long transferClockMs = DATA_NOT_AVAILABLE;
    private long stallStartMs = DATA_NOT_AVAILABLE;

    public void setLoadControl(MISLLoadControl loadControl) {
        this.loadControl = loadControl;
    }

    /**
     * Indicates whether chunk data is available.
     *
     * @return true if chunk data is available, false otherwise
     */
    @Override
    public boolean chunkDataIsAvailable() {
        return chunkData != null;
    }

    /**
     * The index of the most recently downloaded chunk.
     *
     * @return The last chunk's index.
     */
    @Override
    public int lastChunkIndex() {
        if (chunkData == null) {
            return DATA_NOT_AVAILABLE;
        } else {
            return chunkData.size() - 1;
        }
    }

    /**
     * The duration of a downloaded chunk, in ms.
     *
     * @param chunkIndex The index of the chunk.
     * @return The duration of the chunk in ms.
     */
    @Override
    public long chunkDurationMs(int chunkIndex) {
        MediaChunk chunk = chunkData.get(chunkIndex).chunk;
        if (chunk == null) {
            return DATA_NOT_AVAILABLE;
        } else {
            return (chunk.endTimeUs - chunk.startTimeUs);
        }
    }

    /**
     * The arrival time of a downloaded chunk, in ms.
     *
     * @param chunkIndex The index of the chunk.
     * @return The arrival time of the chunk in ms.
     */
    @Override
    public long arrivalTimeMs(int chunkIndex) {
        return chunkData.get(chunkIndex).arrivalTimeMs;
    }

    /**
     * The load duration of a downloaded chunk, in ms.
     *
     * @param chunkIndex The index of the chunk.
     * @return The load duration of the chunk in ms.
     */
    @Override
    public long loadDurationMs(int chunkIndex) {
        return chunkData.get(chunkIndex).loadDurationMs;
    }

    /**
     * The length of time the player has stalled for since starting the
     * video, in ms.
     *
     * @return The player's stall duration in ms.
     */
    @Override
    public long stallDurationMs() {
        return stallDurationMs;
    }

    /**
     * The representation rate of a downloaded chunk, in bits per second.
     *
     * @param chunkIndex The index of the chunk.
     * @return The representation rate of the chunk in bits per second.
     */
    @Override
    public double representationRate(int chunkIndex) {
        MediaChunk chunk = chunkData.get(chunkIndex).chunk;
        if (chunk == null) {
            return DATA_NOT_AVAILABLE;
        } else {
            return chunkData.get(chunkIndex).chunk.trackFormat.bitrate;
        }
    }

    /**
     * The delivery rate of a downloaded chunk, in bits per second.
     *
     * @param chunkIndex The index of the chunk.
     * @return The delivery rate of the chunk in bits per second.
     */
    @Override
    public double deliveryRate(int chunkIndex) {
        AlgorithmData data = chunkData.get(chunkIndex);
        MediaChunk chunk = data.chunk;
        if (chunk == null) {
            return DATA_NOT_AVAILABLE;
        } else {
            long size = data.chunk.bytesLoaded() * 8;
            long loadDurationMs = data.loadDurationMs;
            return size * 1E3 / loadDurationMs;
        }
    }

    /**
     * The actual data rate of a downloaded chunk, in bits per second.
     *
     * @param chunkIndex The index of the chunk.
     * @return The actual data rate of the chunk in bits per second.
     */
    @Override
    public double actualDataRate(int chunkIndex) {
        MediaChunk chunk = chunkData.get(chunkIndex).chunk;
        if (chunk == null) {
            return DATA_NOT_AVAILABLE;
        } else {
            long chunkDurationUs = chunk.endTimeUs - chunk.startTimeUs;
            long size = chunk.bytesLoaded() * 8;

            return size * 1E6 / chunkDurationUs;
        }
    }

    /**
     * The size of a downloaded chunk, in bits.
     *
     * @param chunkIndex The index of the chunk.
     * @return The size of the chunk in bits.
     */
    @Override
    public double size(int chunkIndex) {
        MediaChunk chunk = chunkData.get(chunkIndex).chunk;
        if (chunk == null) {
            return DATA_NOT_AVAILABLE;
        } else {
            return chunk.bytesLoaded() * 8;
        }
    }

    /**
     * The maximum duration of media that the player will attempt to
     * buffer, in ms.
     *
     * @return The player's maximum buffer length in ms.
     */
    @Override
    public double maxBufferMs() {
        if (loadControl != null) {
            return loadControl.getMaxBufferMs();
        } else {
            return DATA_NOT_AVAILABLE;
        }
    }

    /**
     * Gives the listener the last chunk that was downloaded.
     *
     * @param lastChunk The last chunk that was downloaded.
     */
    @Override
    public void giveLastChunk(MediaChunk lastChunk) {
        if (lastChunk == null) {
            return;
        } else if (lastLoadDurationMs == DATA_NOT_AVAILABLE
                || lastArrivalTimeMs == DATA_NOT_AVAILABLE) {
            this.lastChunk = lastChunk;
        } else {
            this.chunkData.add(new AlgorithmData(lastChunk, lastLoadDurationMs, lastArrivalTimeMs));
            lastLoadDurationMs = DATA_NOT_AVAILABLE;
            lastArrivalTimeMs = DATA_NOT_AVAILABLE;
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
        Log.d(TAG, "Transfer ended");
        long nowMs = SystemClock.elapsedRealtime();

        long loadDurationMs = nowMs - transferClockMs;

        if (lastChunk == null) {
            lastLoadDurationMs = loadDurationMs;
            lastArrivalTimeMs = nowMs;
        } else {
            chunkData.add(new AlgorithmData(lastChunk, loadDurationMs, nowMs));
            lastChunk = null;
        }
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
