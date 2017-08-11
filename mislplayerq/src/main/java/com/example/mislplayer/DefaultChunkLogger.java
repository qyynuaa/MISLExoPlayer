package com.example.mislplayer;

import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A default ChunkLogger implementation.
 */
public class DefaultChunkLogger implements ChunkLogger, AdaptiveMediaSourceEventListener,
        ExoPlayer.EventListener, TransferListener<Object> {

    private static class LogEntry {
        private int chunkIndex;
        private long arrivalTimeMs;
        private long loadDurationMs;
        private long stallDurationMs;
        private long repLevelKbps;
        private long actualRateKbps;
        private long byteSize;
        private long bufferLevelUs;
        private long deliveryRateKbps;
        private long chunkDurationMs;

        public LogEntry(long chunkStartTimeMs, long arrivalTimeMs, long loadDurationMs,
                        long stallDurationMs, long repLevelKbps, double deliveryRateKbps,
                        double actualRateKbps, long byteSize,
                        long bufferLevelUs, long chunkDurationMs){
            this.chunkIndex = (int) (chunkStartTimeMs / chunkDurationMs) + 1;
            this.arrivalTimeMs = arrivalTimeMs;
            this.loadDurationMs = loadDurationMs;
            this.stallDurationMs = stallDurationMs;
            this.repLevelKbps = repLevelKbps;
            this.deliveryRateKbps = Math.round(deliveryRateKbps);
            this.actualRateKbps = Math.round(actualRateKbps);
            this.byteSize = byteSize;
            this.bufferLevelUs = bufferLevelUs;
            this.chunkDurationMs = chunkDurationMs;
        }

        public int getChunkIndex(){
            return chunkIndex;
        }
        public long getArrivalTimeMs(){
            return arrivalTimeMs;
        }
        public long getLoadDurationMs(){
            return loadDurationMs;
        }
        public long getStallDurationMs(){
            return stallDurationMs;
        }
        public long getRepLevelKbps(){
            return repLevelKbps;
        }
        public double getDeliveryRateKbps() {return deliveryRateKbps;}
        public double getActualRateKbps(){
            return actualRateKbps;
        }
        public long getByteSize(){
            return byteSize;
        }

        public long getChunkDurationMs(){
            return chunkDurationMs;
        }

        public void setByteSize(long byteSize){this.byteSize=byteSize;}
        public void setDeliveryRateKbps(long deliveryRateKbps){this.deliveryRateKbps = deliveryRateKbps;}
        public void setBufferLevelUs(long bufferLevelUs){this.bufferLevelUs = bufferLevelUs;}
        public void setRepLevelKbps(int repLevelKbps){this.repLevelKbps = repLevelKbps;}
        public void setActualRateKbps(long actualRateKbps){this.actualRateKbps = actualRateKbps;}

        @Override
        public String toString(){
            String logLine = "%5d\t%8d\t%9d\t%10d\t%10d\t%9d\t%9d\t%10d\t%10d\n";
            return String.format(logLine, chunkIndex, arrivalTimeMs, loadDurationMs,
                    stallDurationMs, repLevelKbps, deliveryRateKbps,
                    actualRateKbps, byteSize, bufferLevelUs / 1000);
        }
    }

    private static final String TAG = "DefaultChunkLogger";
    private static final String LOG_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/Logs_Exoplayer";

    private List<LogEntry> log = new ArrayList<>();

    private long stallDurationMs;
    private long lastBufferLevelMs;

    private long stallStartMs;
    private int lastState;
    private boolean currentlyStalling = false;

    private boolean newBufferLevel = false;
    private boolean newChunkData = false;

    private MediaChunk lastBufferChunk;
    private long lastMediaStartTimeMs;
    private long lastElapsedRealtimeMs;
    private long lastLoadDurationMs;
    private long lastMediaEndTimeMs;
    private long lastBytesLoaded;
    private Format lastTrackFormat;

    private long manifestRequestTime = 0;

    /** Logs to file data about all the chunks downloaded so far. */
    @Override
    public void writeLogsToFile() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");
        Date date = new Date();
        File directory = new File(LOG_FILE_PATH);
        File file = new File(directory, "/Log_Segments_ExoPlayer_" + dateFormat.format(date) + ".txt");

        try {
            if (!directory.exists()) {
                directory.mkdirs();
            }
            file.createNewFile();
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(("Seg_#\t\tArr_time\t\tDel_Time\t\tStall_Dur" +
                    "\t\tRep_Level\t\tDel_Rate\t\tAct_Rate\t\tByte_Size" +
                    "\t\tBuff_Level\n").getBytes());
            int index;
            for (LogEntry log : this.log) {
                stream.write(log.toString().getBytes());
            }
            stream.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }


    }

    /** Removes all stored chunk information. */
    @Override
    public void clearChunkInformation() {
        this.log = new ArrayList<>();
    }

    /**
     * Informs the chunk store of the current buffer estimate.
     *
     * @param previous
     * @param bufferedDurationMs
     */
    @Override
    public void updateBufferLevel(MediaChunk previous, long bufferedDurationMs) {
        if (previous != lastBufferChunk) {
            lastBufferChunk = previous;
            lastBufferLevelMs = bufferedDurationMs;
            Log.d(TAG, String.format("Buffer level updated to %d", lastBufferLevelMs));

            if (newChunkData && !currentlyStalling) {
                makeNewLogEntry();
                newChunkData = false;
                stallDurationMs = 0;
            } else {
                // mark that there's a new buffer level value
                newBufferLevel = true;
            }
        }
    }

    private void makeNewLogEntry() {
        long representationRateKbps = lastTrackFormat.bitrate / 1000;
        long deliveryRateKbps = Math.round((double) lastBytesLoaded * 8 / lastLoadDurationMs);
        long chunkDurationMs = lastMediaEndTimeMs - lastMediaStartTimeMs;
        long actualRateKbps = Math.round((double) lastBytesLoaded * 8 / chunkDurationMs);

        LogEntry logEntry = new LogEntry(lastMediaStartTimeMs,
                lastElapsedRealtimeMs - manifestRequestTime, lastLoadDurationMs,
                stallDurationMs, representationRateKbps,
                deliveryRateKbps, actualRateKbps, lastBytesLoaded,
                lastBufferLevelMs, chunkDurationMs);
        log.add(logEntry.getChunkIndex() - 1, logEntry);
    }

    /**
     * Called when a load begins.
     *
     * @param dataSpec             Defines the data being loaded.
     * @param dataType             One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
     *                             being loaded.
     * @param trackType            One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *                             to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat          The format of the track to which the data belongs. Null if the data does
     *                             not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *                             data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData   Optional data associated with the selection of the track to which the
     *                             data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs     The start time of the media being loaded, or {@link C#TIME_UNSET} if
     *                             the load is not for media data.
     * @param mediaEndTimeMs       The end time of the media being loaded, or {@link C#TIME_UNSET} if the
     *                             load is not for media data.
     * @param elapsedRealtimeMs    The value of {@link SystemClock#elapsedRealtime} when the load began.
     */
    @Override
    public void onLoadStarted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs) {

    }

    /**
     * Called when a load ends.
     *
     * @param dataSpec             Defines the data being loaded.
     * @param dataType             One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
     *                             being loaded.
     * @param trackType            One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *                             to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat          The format of the track to which the data belongs. Null if the data does
     *                             not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *                             data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData   Optional data associated with the selection of the track to which the
     *                             data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs     The start time of the media being loaded, or {@link C#TIME_UNSET} if
     *                             the load is not for media data.
     * @param mediaEndTimeMs       The end time of the media being loaded, or {@link C#TIME_UNSET} if the
     *                             load is not for media data.
     * @param elapsedRealtimeMs    The value of {@link SystemClock#elapsedRealtime} when the load ended.
     * @param loadDurationMs       The duration of the load.
     * @param bytesLoaded          The number of bytes that were loaded.
     */
    @Override
    public void onLoadCompleted(DataSpec dataSpec, int dataType,
                                int trackType, Format trackFormat,
                                int trackSelectionReason,
                                Object trackSelectionData,
                                long mediaStartTimeMs, long mediaEndTimeMs,
                                long elapsedRealtimeMs, long loadDurationMs,
                                long bytesLoaded) {
        if (trackType == C.TRACK_TYPE_VIDEO && mediaStartTimeMs != C.TIME_UNSET) {
            this.lastTrackFormat = trackFormat;
            this.lastMediaStartTimeMs = mediaStartTimeMs;
            this.lastMediaEndTimeMs = mediaEndTimeMs;
            this.lastLoadDurationMs = loadDurationMs;
            this.lastElapsedRealtimeMs = elapsedRealtimeMs;
            this.lastBytesLoaded = bytesLoaded;

            if (newBufferLevel && !currentlyStalling) {
                makeNewLogEntry();
                newBufferLevel = false;
                stallDurationMs = 0;
            } else {
                // mark that there's new chunk data
                newChunkData = true;
            }
        }
    }

    /**
     * Called when a load is canceled.
     *
     * @param dataSpec             Defines the data being loaded.
     * @param dataType             One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
     *                             being loaded.
     * @param trackType            One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *                             to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat          The format of the track to which the data belongs. Null if the data does
     *                             not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *                             data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData   Optional data associated with the selection of the track to which the
     *                             data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs     The start time of the media being loaded, or {@link C#TIME_UNSET} if
     *                             the load is not for media data.
     * @param mediaEndTimeMs       The end time of the media being loaded, or {@link C#TIME_UNSET} if the
     *                             load is not for media data.
     * @param elapsedRealtimeMs    The value of {@link SystemClock#elapsedRealtime} when the load was
     *                             canceled.
     * @param loadDurationMs       The duration of the load up to the point at which it was canceled.
     * @param bytesLoaded          The number of bytes that were loaded prior to cancelation.
     */
    @Override
    public void onLoadCanceled(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {

    }

    /**
     * Called when a load error occurs.
     * <p>
     * The error may or may not have resulted in the load being canceled, as indicated by the
     * {@code wasCanceled} parameter. If the load was canceled, {@link #onLoadCanceled} will
     * <em>not</em> be called in addition to this method.
     *
     * @param dataSpec             Defines the data being loaded.
     * @param dataType             One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
     *                             being loaded.
     * @param trackType            One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *                             to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat          The format of the track to which the data belongs. Null if the data does
     *                             not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *                             data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData   Optional data associated with the selection of the track to which the
     *                             data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs     The start time of the media being loaded, or {@link C#TIME_UNSET} if
     *                             the load is not for media data.
     * @param mediaEndTimeMs       The end time of the media being loaded, or {@link C#TIME_UNSET} if the
     *                             load is not for media data.
     * @param elapsedRealtimeMs    The value of {@link SystemClock#elapsedRealtime} when the error
     *                             occurred.
     * @param loadDurationMs       The duration of the load up to the point at which the error occurred.
     * @param bytesLoaded          The number of bytes that were loaded prior to the error.
     * @param error                The load error.
     * @param wasCanceled          Whether the load was canceled as a result of the error.
     */
    @Override
    public void onLoadError(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded, IOException error, boolean wasCanceled) {

    }

    /**
     * Called when data is removed from the back of a media buffer, typically so that it can be
     * re-buffered in a different format.
     *
     * @param trackType        The type of the media. One of the {@link C} {@code TRACK_TYPE_*} constants.
     * @param mediaStartTimeMs The start time of the media being discarded.
     * @param mediaEndTimeMs   The end time of the media being discarded.
     */
    @Override
    public void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs) {

    }

    /**
     * Called when a downstream format change occurs (i.e. when the format of the media being read
     * from one or more {@link SampleStream}s provided by the source changes).
     *
     * @param trackType            The type of the media. One of the {@link C} {@code TRACK_TYPE_*} constants.
     * @param trackFormat          The format of the track to which the data belongs. Null if the data does
     *                             not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *                             data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData   Optional data associated with the selection of the track to which the
     *                             data belongs. Null if the data does not belong to a track.
     * @param mediaTimeMs          The media time at which the change occurred.
     */
    @Override
    public void onDownstreamFormatChanged(int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaTimeMs) {

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
        if (lastState == ExoPlayer.STATE_READY && playbackState == ExoPlayer.STATE_BUFFERING) {
            stallStartMs = SystemClock.elapsedRealtime();
            currentlyStalling = true;
        } else if (currentlyStalling && playbackState == ExoPlayer.STATE_READY) {
            long nowMs = SystemClock.elapsedRealtime();
            stallDurationMs += nowMs - stallStartMs;
            currentlyStalling = false;

            if (newBufferLevel && newChunkData) {
                // we were waiting on stall data
                makeNewLogEntry();
                newBufferLevel = false;
                newChunkData = false;
                stallDurationMs = 0;
            }
        }
        lastState = playbackState;
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

    /**
     * Called when a transfer starts.
     *
     * @param source   The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        if (manifestRequestTime == 0) {
            manifestRequestTime = SystemClock.elapsedRealtime();
            Log.d(TAG, String.format("Updated manifest request time to %d.", manifestRequestTime));
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
