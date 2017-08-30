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
        ExoPlayer.EventListener, ManifestListener.ManifestRequestTimeReceiver {

    /**
     * An entry in the log.
     */
    private static class LogEntry {
        private int chunkIndex;
        private long arrivalTimeMs;
        private long loadDurationMs;
        private long stallDurationMs;
        private long repLevelKbps;
        private long actualRateKbps;
        private long byteSize;
        private long bufferLevelMs;
        private long deliveryRateKbps;
        private long chunkDurationMs;

        public LogEntry(long chunkStartTimeMs, long arrivalTimeMs, long loadDurationMs,
                        long stallDurationMs, long repLevelKbps, double deliveryRateKbps,
                        double actualRateKbps, long byteSize,
                        long bufferLevelMs, long chunkDurationMs){
            this.chunkIndex = (int) (Math.round((double) chunkStartTimeMs / chunkDurationMs)) + 1;
            this.arrivalTimeMs = arrivalTimeMs;
            this.loadDurationMs = loadDurationMs;
            this.stallDurationMs = stallDurationMs;
            this.repLevelKbps = repLevelKbps;
            this.deliveryRateKbps = Math.round(deliveryRateKbps);
            this.actualRateKbps = Math.round(actualRateKbps);
            this.byteSize = byteSize;
            this.bufferLevelMs = bufferLevelMs;
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
        public void setBufferLevelMs(long bufferLevelMs){this.bufferLevelMs = bufferLevelMs;}
        public void setRepLevelKbps(int repLevelKbps){this.repLevelKbps = repLevelKbps;}
        public void setActualRateKbps(long actualRateKbps){this.actualRateKbps = actualRateKbps;}

        @Override
        public String toString(){
            String logLine = "%5d\t%8d\t%9d\t%10d\t%10d\t%9d\t%9d\t%10d\t%10d\n";
            return String.format(logLine, chunkIndex, arrivalTimeMs, loadDurationMs,
                    stallDurationMs, repLevelKbps, deliveryRateKbps,
                    actualRateKbps, byteSize, bufferLevelMs);
        }
    }

    private static final String TAG = "DefaultChunkLogger";
    private static final String LOG_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/Logs_Exoplayer";

    private ExoPlayer player;

    private List<LogEntry> log = new ArrayList<>();

    private long stallDurationMs;
    private long lastBufferLevelMs;

    private long stallStartMs;
    private int lastState;
    private boolean currentlyStalling = false;

    private boolean newChunkData = false;

    private long lastMediaStartTimeMs;
    private long lastElapsedRealtimeMs;
    private long lastLoadDurationMs;
    private long lastMediaEndTimeMs;
    private long lastBytesLoaded;
    private Format lastTrackFormat;

    private long manifestRequestTime = 0;

    /**
     * Sets the chunk logger's player reference.
     *
     * <p>The logger uses this reference to get buffer level values.
     *
     * @param player The player the chunk logger is logging from.
     */
    public void setPlayer(ExoPlayer player) {
        this.player = player;
    }

    @Override
    public void writeLogsToFile() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");
        Date date = new Date();
        File directory = new File(LOG_FILE_PATH);
        File file = new File(directory, "/" + dateFormat.format(date) + "_Chunk_Log.txt");

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

    @Override
    public void clearChunkInformation() {
        this.log = new ArrayList<>();
    }

    /**
     * Adds a new entry to the log.
     */
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
        int chunkArrayIndex = logEntry.getChunkIndex() - 1;
        if (chunkArrayIndex < log.size()) {
            log.set(chunkArrayIndex, logEntry);
        } else {
            log.add(logEntry);
        }
    }

    // ManifestRequestTimeReceiver implementation

    @Override
    public void giveManifestRequestTime(long manifestRequestTime) {
        this.manifestRequestTime = manifestRequestTime;
    }

    // AdaptiveMediaSourceEventListener implementation

    @Override
    public void onLoadStarted(DataSpec dataSpec, int dataType,
            int trackType, Format trackFormat, int trackSelectionReason,
            Object trackSelectionData, long mediaStartTimeMs,
            long mediaEndTimeMs, long elapsedRealtimeMs) {}

    @Override
    public void onLoadCompleted(DataSpec dataSpec, int dataType,
                                int trackType, Format trackFormat,
                                int trackSelectionReason,
                                Object trackSelectionData,
                                long mediaStartTimeMs, long mediaEndTimeMs,
                                long elapsedRealtimeMs, long loadDurationMs,
                                long bytesLoaded) {
        Log.d(TAG, "Load completed");
        if (dataType == C.DATA_TYPE_MEDIA && mediaStartTimeMs != C.TIME_UNSET) {
            this.lastTrackFormat = trackFormat;
            this.lastMediaStartTimeMs = mediaStartTimeMs;
            this.lastMediaEndTimeMs = mediaEndTimeMs;
            this.lastLoadDurationMs = loadDurationMs;
            this.lastElapsedRealtimeMs = elapsedRealtimeMs;
            this.lastBytesLoaded = bytesLoaded;
            this.lastBufferLevelMs = player.getBufferedPosition() - player.getCurrentPosition();

            if (!currentlyStalling) {
                makeNewLogEntry();
                stallDurationMs = 0;
            } else {
                // mark that there's new chunk data
                newChunkData = true;
            }
        }
    }

    @Override
    public void onLoadCanceled(DataSpec dataSpec, int dataType,
            int trackType, Format trackFormat, int trackSelectionReason,
            Object trackSelectionData, long mediaStartTimeMs,
            long mediaEndTimeMs, long elapsedRealtimeMs,
            long loadDurationMs, long bytesLoaded) {}

    @Override
    public void onLoadError(DataSpec dataSpec, int dataType, int trackType,
            Format trackFormat, int trackSelectionReason,
            Object trackSelectionData, long mediaStartTimeMs,
            long mediaEndTimeMs, long elapsedRealtimeMs,
            long loadDurationMs, long bytesLoaded, IOException error,
            boolean wasCanceled) {}

    @Override
    public void onUpstreamDiscarded(int trackType, long mediaStartTimeMs,
            long mediaEndTimeMs) {}

    @Override
    public void onDownstreamFormatChanged(int trackType,
            Format trackFormat, int trackSelectionReason,
            Object trackSelectionData, long mediaTimeMs) {}

    // ExoPlayer.EventListener implementation

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {}

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups,
            TrackSelectionArray trackSelections) {}

    @Override
    public void onLoadingChanged(boolean isLoading) {}

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (lastState == ExoPlayer.STATE_READY
                && playbackState == ExoPlayer.STATE_BUFFERING) {
            stallStartMs = SystemClock.elapsedRealtime();
            currentlyStalling = true;
        } else if (currentlyStalling
                && playbackState == ExoPlayer.STATE_READY) {
            long nowMs = SystemClock.elapsedRealtime();
            stallDurationMs += nowMs - stallStartMs;
            currentlyStalling = false;

            if (newChunkData) {
                // we were waiting on stall data
                makeNewLogEntry();
                newChunkData = false;
                stallDurationMs = 0;
            }
        }
        lastState = playbackState;
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {}

    @Override
    public void onPositionDiscontinuity() {}

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}
}
