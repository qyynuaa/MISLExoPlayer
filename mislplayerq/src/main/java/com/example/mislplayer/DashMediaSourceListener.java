package com.example.mislplayer;

import android.os.SystemClock;
import android.util.Log;

import com.example.mislplayer.Algorithm_Parameters.ArbiterParameters;
import com.example.mislplayer.Algorithm_Parameters.OscarParameters;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.IOException;
import java.util.ArrayList;

import static java.lang.Math.round;

/**
 * Created by Quentin L on 31/05/2017.
 */

public class DashMediaSourceListener implements AdaptiveMediaSourceEventListener {
    private int segmentNumber=0;
    private long segmentDuration=0;
    private ArrayList<LogSegment> allSegLog= new ArrayList<LogSegment>();
    private long arrivalTime=0;
    private long deliveryTime=0;
    private long stallDuration=0;
    private int repLevel=0;
    private long deliveryRate=0;
    private long actualRate=0;
    private long byteSize=0;
    private long bufferLevel=0;
    private int i=0;
    public static LogSegment logSegment;
    private long currentTime= System.currentTimeMillis();

    /**
     * Called when a load begins.
     *
     * @param dataSpec Defines the data being loaded.
     * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
     *     being loaded.
     * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat The format of the track to which the data belongs. Null if the data does
     *     not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData Optional data associated with the selection of the track to which the
     *     data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs The start time of the media being loaded, or {@link C#TIME_UNSET} if
     *     the load is not for media data.
     * @param mediaEndTimeMs The end time of the media being loaded, or {@link C#TIME_UNSET} if the
     *     load is not for media data.
     * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} when the load began.
     */
    @Override
    public void onLoadStarted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                              int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                              long mediaEndTimeMs, long elapsedRealtimeMs) {


    }

    public ArrayList<LogSegment> getSegInfos (){
        return allSegLog;
    }
    /**
     * Called when a load ends.
     *
     * @param dataSpec Defines the data being loaded.
     * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
     *     being loaded.
     * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat The format of the track to which the data belongs. Null if the data does
     *     not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData Optional data associated with the selection of the track to which the
     *     data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs The start time of the media being loaded, or {@link C#TIME_UNSET} if
     *     the load is not for media data.
     * @param mediaEndTimeMs The end time of the media being loaded, or {@link C#TIME_UNSET} if the
     *     load is not for media data.
     * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} when the load ended.
     * @param loadDurationMs The duration of the load.
     * @param bytesLoaded The number of bytes that were loaded.
     */
    @Override
    public void onLoadCompleted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                                int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                                long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded){
        if(dataType!=1)
        Log.d("ah1","ah1");
        if(dataType==1 && trackFormat.sampleMimeType.contains("video") ) {
            Log.d("ah2","ah2");
            stallDuration=0;
            segmentDuration=mediaEndTimeMs-mediaStartTimeMs;
            segmentNumber = (int)round(mediaEndTimeMs/segmentDuration);
            ArrayList<FutureSegmentInfos> futureSegmentInfos=PlayerActivity.futureSegmentInfos;
            arrivalTime = System.currentTimeMillis()-currentTime;
            deliveryTime = loadDurationMs;
            repLevel = trackFormat.bitrate / 1000;
            Log.d("SEG-QUAL",repLevel+"");
            actualRate= (bytesLoaded * 8) *1000 /(segmentDuration);
            byteSize=bytesLoaded;
            bufferLevel=PlayerActivity.player.getBufferedPosition()-PlayerActivity.player.getCurrentPosition();
            if(deliveryTime >0) deliveryRate= (bytesLoaded / deliveryTime)*8;
            Log.d("bufferL",bufferLevel+"");
            if(ExoPlayerListener.getStallDuration()!=0 && ExoPlayerListener.flag>0){
                stallDuration=ExoPlayerListener.getStallDuration();
                Log.d("STALL",""+stallDuration);
                ExoPlayerListener.setStallDuration(0);
            }

            logSegment = new LogSegment(segmentNumber, arrivalTime, deliveryTime, stallDuration, repLevel, deliveryRate, actualRate, byteSize, bufferLevel, segmentDuration, null);

            allSegLog.add(i, logSegment);
            Log.d("LSN1",logSegment.getSegNumber()+"");
            i++;
        }
    }



    /**
     * Called when a load is canceled.
     *
     * @param dataSpec Defines the data being loaded.
     * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
     *     being loaded.
     * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat The format of the track to which the data belongs. Null if the data does
     *     not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData Optional data associated with the selection of the track to which the
     *     data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs The start time of the media being loaded, or {@link C#TIME_UNSET} if
     *     the load is not for media data.
     * @param mediaEndTimeMs The end time of the media being loaded, or {@link C#TIME_UNSET} if the
     *     load is not for media data.
     * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} when the load was
     *     canceled.
     * @param loadDurationMs The duration of the load up to the point at which it was canceled.
     * @param bytesLoaded The number of bytes that were loaded prior to cancelation.
     */
    @Override
    public void onLoadCanceled(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                               int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                               long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded){

        Log.d("CANCEL","Load was canceled");
    }

    /**
     * Called when a load error occurs.
     * <p>
     * The error may or may not have resulted in the load being canceled, as indicated by the
     * {@code wasCanceled} parameter. If the load was canceled, {@link #onLoadCanceled} will
     * <em>not</em> be called in addition to this method.
     *
     * @param dataSpec Defines the data being loaded.
     * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
     *     being loaded.
     * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat The format of the track to which the data belongs. Null if the data does
     *     not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData Optional data associated with the selection of the track to which the
     *     data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs The start time of the media being loaded, or {@link C#TIME_UNSET} if
     *     the load is not for media data.
     * @param mediaEndTimeMs The end time of the media being loaded, or {@link C#TIME_UNSET} if the
     *     load is not for media data.
     * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} when the error
     *     occurred.
     * @param loadDurationMs The duration of the load up to the point at which the error occurred.
     * @param bytesLoaded The number of bytes that were loaded prior to the error.
     * @param error The load error.
     * @param wasCanceled Whether the load was canceled as a result of the error.
     */
    @Override
    public void onLoadError(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                            int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                            long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded,
                            IOException error, boolean wasCanceled){
        Log.d("ERROR","Load ");
    }

    /**
     * Called when data is removed from the back of a media buffer, typically so that it can be
     * re-buffered in a different format.
     *
     * @param trackType The type of the media. One of the {@link C} {@code TRACK_TYPE_*} constants.
     * @param mediaStartTimeMs The start time of the media being discarded.
     * @param mediaEndTimeMs The end time of the media being discarded.
     */
    @Override
    public void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs){

    }

    /**
     * Called when a downstream format change occurs (i.e. when the format of the media being read
     * from one or more {@link SampleStream}s provided by the source changes).
     *
     * @param trackType The type of the media. One of the {@link C} {@code TRACK_TYPE_*} constants.
     * @param trackFormat The format of the track to which the data belongs. Null if the data does
     *     not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData Optional data associated with the selection of the track to which the
     *     data belongs. Null if the data does not belong to a track.
     * @param mediaTimeMs The media time at which the change occurred.
     */
    @Override
    public void onDownstreamFormatChanged(int trackType, Format trackFormat, int trackSelectionReason,
                                          Object trackSelectionData, long mediaTimeMs){

    }

    /**
     * Returns {@code getFirstSegmentNum()} if the index has no segments or if the given media time is
     * earlier than the start of the first segment. Returns {@code getFirstSegmentNum() +
     * getSegmentCount() - 1} if the given media time is later than the end of the last segment.
     * Otherwise, returns the segment number of the segment containing the given media time.
     *
     * @param timeUs The time in microseconds.
     * @param periodDurationUs The duration of the enclosing period in microseconds, or
     *     {@link C#TIME_UNSET} if the period's duration is not yet known.
     * @return The segment number of the corresponding segment.
     */


}
