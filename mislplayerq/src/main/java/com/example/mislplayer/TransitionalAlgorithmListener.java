package com.example.mislplayer;

import android.os.SystemClock;
import android.util.Log;

import com.example.mislplayer.Algorithm_Parameters.ArbiterParameters;
import com.example.mislplayer.Algorithm_Parameters.DashParameters;
import com.example.mislplayer.Algorithm_Parameters.OscarParameters;
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

import java.util.ArrayList;

/**
 * A transitional listener, to be used while migrating code.
 */

public class TransitionalAlgorithmListener implements ChunkListener,
        TransferListener, ExoPlayer.EventListener {

    private static final String TAG = "TransitionalAL";

    private MediaChunk lastChunk;

    private long arrivalTimeMs;
    private long loadDurationMs;
    private long transferClockMs;

    private int numberOfStreams = 0;
    private long byteClock;

    public static LogSegment logSegment;

    private ArrayList<LogSegment> allSegLog = new ArrayList<>();

    public ArrayList<LogSegment> getSegInfos() {return allSegLog;}

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

        int segmentNumber = lastChunk.chunkIndex;
        Log.d(TAG, String.format("Chunk index = %d", segmentNumber));

        /** The duration of the segment in ms. */
        long segmentDurationMs = (lastChunk.endTimeUs - lastChunk.startTimeUs) / 1000;
        Log.d(TAG, String.format("Chunk duration = %d ms", segmentDurationMs));

        /** The representation level of the segment's track in kbps. */
        int representationLevelKbps = lastChunk.trackFormat.bitrate / 1000;

        /** The actual rate of the segment in bits per second. */
        long actualRatebps = lastChunk.bytesLoaded() * 8000 / segmentDurationMs;

        /** The size of the segment in bytes. */
        long byteSize = lastChunk.bytesLoaded();
        Log.d(TAG, String.format("Chunk size = %d bytes", byteSize));

        /** The buffer level of the player in ms. */
        long bufferLevelMs = PlayerActivity.player.getBufferedPosition() - PlayerActivity.player.getCurrentPosition();

        long deliveryRateKbps = 0;

        if (loadDurationMs > 0) {
            /** The delivery rate of the chunk, in kbps. */
            deliveryRateKbps = byteSize * 8 / loadDurationMs;
            Log.d(TAG, String.format("Load duration = %d ms", loadDurationMs));
            Log.d(TAG, String.format("Delivery rate = %d kbps", deliveryRateKbps));
        }

        /** The amount of time the player has spent stalling, in ms. */
        long stallDurationMs = 0;

        DashParameters parameterObject = null;

        if(PlayerActivity.ALGORITHM_TYPE.equals("OSCAR-H")) {
            parameterObject = new OscarParameters(0.999, 0.000, 0.5, 10, 10, 2, 0, 1, 4);
        }
        else if(PlayerActivity.ALGORITHM_TYPE.equals("ARBITER")){
            parameterObject = new ArbiterParameters(10,0.4,0.3,0.5,1.5,5);
        }

        logSegment = new LogSegment(segmentNumber, arrivalTimeMs,
                loadDurationMs, stallDurationMs, representationLevelKbps,
                deliveryRateKbps, actualRatebps, byteSize, bufferLevelMs,
                segmentDurationMs, parameterObject);

        allSegLog.add(logSegment);
        this.lastChunk = lastChunk;
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
        Log.d(TAG, String.format("Transfer clock started at %d", transferClockMs));

        numberOfStreams++;
        Log.d(TAG, String.format("There are %d streams", numberOfStreams));
        
        byteClock = 0;
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
    }

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    @Override
    public void onTransferEnd(Object source) {
        arrivalTimeMs = SystemClock.elapsedRealtime();
        loadDurationMs = arrivalTimeMs - transferClockMs;
        Log.d(TAG, String.format("Transfer finished at %d", arrivalTimeMs));
        Log.d(TAG, String.format("Data transferred = %d bytes", byteClock));
        
        numberOfStreams--;
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
