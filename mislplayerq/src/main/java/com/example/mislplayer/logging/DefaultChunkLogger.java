package com.example.mislplayer.logging;

import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
        private final int chunkIndex;
        private final long arrivalTimeMs;
        private final long loadDurationMs;
        private final long stallDurationMs;
        private final long repLevelKbps;
        private final long actualRateKbps;
        private final long byteSize;
        private final long bufferLevelMs;
        private final long deliveryRateKbps;
        private final long chunkDurationMs;

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
    }

    private static final String TAG = "DefaultChunkLogger";

    private ExoPlayer player;

    private LogBuilder logBuilder;

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
     * Creates a default {@link ChunkLogger} that uses a
     * {@link DefaultLogBuilder} to build its log.
     *
     * @param logFile The file the log should be saved to.
     */
    public DefaultChunkLogger(File logFile) {
        this(new DefaultLogBuilder(logFile));
    }

    /**
     * Creates a default {@link ChunkLogger} that uses a specific
     * {@link LogBuilder} to build its log.
     *
     * @param builder The log builder the logger should use.
     */
    public DefaultChunkLogger(LogBuilder builder) {
        logBuilder = builder;
    }

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

        for (LogEntry entry : log) {
            logBuilder.startEntry();
            logBuilder.chunkIndex(entry.chunkIndex);
            logBuilder.actualRate(entry.actualRateKbps);
            logBuilder.arrivalTime(entry.arrivalTimeMs);
            logBuilder.bufferLevel(entry.bufferLevelMs);
            logBuilder.byteSize(entry.byteSize);
            logBuilder.deliveryRate(entry.deliveryRateKbps);
            logBuilder.loadDuration(entry.loadDurationMs);
            logBuilder.representationRate(entry.repLevelKbps);
            logBuilder.stallDuration(entry.stallDurationMs);
            logBuilder.finishEntry();
        }

        logBuilder.finishLog();
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
        int chunkArrayIndex = logEntry.chunkIndex - 1;
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
