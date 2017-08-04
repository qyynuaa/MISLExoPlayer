package com.example.mislplayer.sampling;

import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A default chunk store.
 */
public class DefaultChunkStore implements ChunkStore, AdaptiveMediaSourceEventListener {

    private static class LogEntry {
        private int chunkIndex;
        private long arrivalTimeMs;
        private long loadDurationMs;
        private long stallDurationMs;
        private long repLevelKbps;
        private double actualRateKbps;
        private long byteSize;
        private long bufferLevelUs;
        private double deliveryRateKbps;
        private long chunkDurationMs;

        public LogEntry(long chunkStartTimeMs, long arrivalTimeMs, long loadDurationMs,
                        long stallDurationMs, long repLevelKbps, double deliveryRateKbps,
                        double actualRateKbps, long byteSize,
                        long bufferLevelUs, long chunkDurationMs){
            this.chunkIndex = (int) (chunkStartTimeMs / 4000) + 1;
            this.arrivalTimeMs = arrivalTimeMs;
            this.loadDurationMs = loadDurationMs;
            this.stallDurationMs = stallDurationMs;
            this.repLevelKbps = repLevelKbps;
            this.deliveryRateKbps = deliveryRateKbps;
            this.actualRateKbps = actualRateKbps;
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
            String logLine = "%5d\t%8d\t%9d\t%10d\t%10d\t%9g\t%9g\t%10d\t%10d\n";
            return String.format(logLine, chunkIndex, arrivalTimeMs, loadDurationMs,
                    stallDurationMs, repLevelKbps, deliveryRateKbps,
                    actualRateKbps, byteSize, bufferLevelUs / 1000);
        }
    }

    private static final String TAG = "DefaultChunkStore";
    private static final String LOG_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/Logs_Exoplayer";

    private List<LogEntry> log = new ArrayList<>();

    private long totalStallDurationMs;
    private long currentBufferLevelMs;

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
     * Informs the chunk store of a new stall.
     *
     * @param stallDurationMs
     */
    @Override
    public void newStall(long stallDurationMs) {
        totalStallDurationMs += stallDurationMs;
    }

    /**
     * Informs the chunk store of the current buffer estimate.
     *
     * @param bufferedDurationMs
     */
    @Override
    public void updateBufferLevel(long bufferedDurationMs) {
        currentBufferLevelMs = bufferedDurationMs;
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
            Log.d(TAG, String.format("Media start time: %d", mediaStartTimeMs));
            Log.d(TAG, String.format("Media end time: %d", mediaEndTimeMs));
            Log.d(TAG, String.format("Media load duration: %d", loadDurationMs));
            long representationRateKbps = trackFormat.bitrate / 1000;
            double deliveryRateKbps = bytesLoaded * 8 / loadDurationMs;
            long chunkDurationMs = mediaEndTimeMs - mediaStartTimeMs;
            double actualRateKbps = (double) bytesLoaded * 8000 / chunkDurationMs;

            log.add(new LogEntry(mediaStartTimeMs, elapsedRealtimeMs,
                    loadDurationMs, totalStallDurationMs,
                    representationRateKbps, deliveryRateKbps, actualRateKbps,
                    bytesLoaded, currentBufferLevelMs, chunkDurationMs));
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
}
