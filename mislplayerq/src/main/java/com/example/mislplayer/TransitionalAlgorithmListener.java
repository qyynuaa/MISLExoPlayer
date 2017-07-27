package com.example.mislplayer;

import android.os.Environment;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static com.google.android.exoplayer2.ExoPlayer.STATE_BUFFERING;
import static com.google.android.exoplayer2.ExoPlayer.STATE_READY;
import static java.lang.Math.min;

/**
 * A transitional listener, to be used while migrating code.
 */

public class TransitionalAlgorithmListener implements ChunkListener,
        TransferListener, ExoPlayer.EventListener {

    private static final String TAG = "TransitionalAL";

    public static final String LOG_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/Logs_Exoplayer";

    private MediaChunk lastChunk;

    private long arrivalTimeMs;
    private long loadDurationMs;
    private long transferClockMs;

    private int numberOfStreams = 0;
    private long byteClock;

    private long stallClockMs;
    private int lastPlaybackState;
    private long stallDurationMs;

    private long mpdDuration;

    private ArrayList<ChunkInformation> downloadedChunkInfo = new ArrayList<>();

    private PlayerActivity playerActivity;

    public TransitionalAlgorithmListener(PlayerActivity playerActivity) {
        this.playerActivity = playerActivity;
    }

    public ArrayList<ChunkInformation> getSegInfos() {return downloadedChunkInfo;}

    /**
     * Indicates that data on previous chunks is not available.
     *
     * @return true if data on previous chunks is not available, false
     * otherwise.
     */
    public boolean chunkDataNotAvailable() {return downloadedChunkInfo.size() == 0;}

    /**
     * Calculates an appropriate window size, based on the number of
     * downloaded chunks available.
     *
     * @param window The ideal window size.
     * @return The appropriate window size.
     */
    public int getWindowSize(int window) {
        return min(window, lastChunkInfo().getSegNumber());
    }

    /** Gives the current maximum buffer length the player is aiming for. */
    public long getMaxBufferMs() {
        return playerActivity.getMaxBufferMs();
    }

    /**
     * Provides the last few throughput samples.
     *
     * @param window The number of throughput samples to provide.
     * @return The last few throughput samples.
     */
    public double[] getThroughputSamples(int window) {
        double[] rateSamples = new double[window];
        for (int i = 1; i <= window; i++) {
            int chunkIndex = lastChunkInfo().getSegNumber();
            rateSamples[i - 1] = (double) getSegInfos().get(chunkIndex - i).getDeliveryRate();
        }
        return rateSamples;
    }

    /** Provides the duration of the current mpd. */
    public long mpdDuration() {
        return mpdDuration;
    }

    /**
     * Gives the listener the duration of the mpd.
     *
     * @param duration The duration of the mpd.
     */
    public void giveMpdDuration(long duration) {
        mpdDuration = duration;
    }

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

        long deliveryRateKbps = 0;

        if (loadDurationMs > 0) {
            /** The delivery rate of the chunk, in kbps. */
            deliveryRateKbps = byteSize * 8 / loadDurationMs;
            Log.d(TAG, String.format("Load duration = %d ms", loadDurationMs));
            Log.d(TAG, String.format("Delivery rate = %d kbps", deliveryRateKbps));
        }

        ChunkInformation lastChunkInfo = new ChunkInformation(segmentNumber, arrivalTimeMs,
                loadDurationMs, stallDurationMs, representationLevelKbps,
                deliveryRateKbps, actualRatebps, byteSize, 0, segmentDurationMs);

        downloadedChunkInfo.add(lastChunkInfo);
        this.lastChunk = lastChunk;
    }

    /** Returns the index of the most recently downloaded chunk. */
    public int lastChunkIndex(){
        return lastChunkInfo().getSegNumber();
    }

    /** Returns the arrival time of the last chunk, in ms. */
    public long lastArrivalTimeMs(){
        return lastChunkInfo().getArrivalTime();
    }

    /** Returns the amount of time it took to load the last chunk, in ms. */
    public long lastLoadDurationMs(){
        return lastChunkInfo().getDeliveryTime();
    }

    /** Returns the total amount of time the player has spent stalling, in ms. */
    public long stallDurationMs(){
        return lastChunkInfo().getStallDuration();
    }

    /** Returns the representation level of the most recently downloaded chunk, in kbps. */
    public int lastRepLevelKbps(){
        return lastChunkInfo().getRepLevel();
    }

    /** Returns the delivery rate of the most recently downloaded chunk, in kbps. */
    public long lastDeliveryRateKbps() {return lastChunkInfo().getDeliveryRate();}

    /** Returns the actual data rate of the most recently downloaded chunk, in bits per second. */
    public long actualRatebps(){
        return lastChunkInfo().getActionRate();
    }

    /** Returns the size of the most recently downloaded chunk, in bytes. */
    public long lastByteSize(){
        return lastChunkInfo().getByteSize();
    }

    /** Returns the duration of the most recently downloaded chunk, in ms. */
    public long lastChunkDurationMs(){
        return lastChunkInfo().getSegmentDuration();
    }

    /** Logs to file data about all the chunks downloaded so far. */
    public void writeLogsToFile() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");
        Date date = new Date();
        File file = new File(LOG_FILE_PATH, "/Log_Segments_ExoPlayer_" + dateFormat.format(date) + ".txt");
        try {
            FileOutputStream stream = new FileOutputStream(file);
            stream.write("Seg_#\t\tArr_time\t\tDel_Time\t\tStall_Dur\t\tRep_Level\t\tDel_Rate\t\tAct_Rate\t\tByte_Size\t\tBuff_Level\n".getBytes());
            int index;
            for (index = 0; index < downloadedChunkInfo.size(); index++) {
                if (downloadedChunkInfo.get(index) != null) {
                    stream.write(downloadedChunkInfo.get(index).toString().getBytes());
                    stream.write("\n".getBytes());
                }

            }
            stream.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }


    }

    private ChunkInformation lastChunkInfo() {
        return downloadedChunkInfo.get(downloadedChunkInfo.size() - 1);
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
        numberOfStreams++;
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
        if (playbackState == STATE_BUFFERING) {
            stallClockMs = SystemClock.elapsedRealtime();
        } else if (playbackState == STATE_READY && lastPlaybackState == STATE_BUFFERING) {
            long nowMs = SystemClock.elapsedRealtime();
            stallDurationMs += nowMs - stallClockMs;
        }
        lastPlaybackState = playbackState;
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

    private static class ChunkInformation {
        private int segNumber;
        private long arrivalTime;
        private long deliveryTime;
        private long stallDuration;
        private int repLevel;
        private long actionRate;
        private long byteSize;
        private long bufferLevel;
        private long deliveryRate;
        private long segmentDuration;

        public ChunkInformation(int segNumber, long arrivalTime, long deliveryTime,
                                long stallDuration, int repLevel, long deliveryRate,
                                long actionRate, long byteSize, long bufferLevel, long segmentDuration){
            this.segNumber=segNumber;
            this.arrivalTime=arrivalTime;
            this.deliveryTime=deliveryTime;
            this.stallDuration=stallDuration;
            this.repLevel=repLevel;
            this.deliveryRate=deliveryRate;
            this.actionRate=actionRate;
            this.byteSize=byteSize;
            this.bufferLevel=bufferLevel;
            this.segmentDuration=segmentDuration;
        }

        public int getSegNumber(){
            return segNumber;
        }
        public long getArrivalTime(){
            return arrivalTime;
        }
        public long getDeliveryTime(){
            return deliveryTime;
        }
        public long getStallDuration(){
            return stallDuration;
        }
        public int getRepLevel(){
            return repLevel;
        }
        public long getDeliveryRate() {return deliveryRate;}
        public long getActionRate(){
            return actionRate;
        }
        public long getByteSize(){
            return byteSize;
        }

        public long getSegmentDuration(){
            return segmentDuration;
        }

        public void setByteSize(long byteSize){this.byteSize=byteSize;}
        public void setDeliveryRate(long deliveryRate){this.deliveryRate=deliveryRate;}
        public void setBufferLevel(long bufferLevel){this.bufferLevel=bufferLevel;}
        public void setRepLevel(int repLevel){this.repLevel=repLevel;}
        public void setActionRate(long actionRate){this.actionRate=actionRate;}

        @Override
        public String toString(){
            String segNum= (getSegNumber())+"";
            String arrivalTime = getArrivalTime()+"";
            String deliveryTime = getDeliveryTime()+"";
            String stallDuration = getStallDuration()+"";
            String repLevel = getRepLevel()+"";
            String deliveryRate = getDeliveryRate()+"";
            String actionRate = getActionRate()+"";
            String byteSize = getByteSize()+"";
            while (segNum.length()!=5){
                segNum = " "+segNum;
            }
            while(arrivalTime.length()!=8){
                arrivalTime = " "+arrivalTime;
            }
            while(deliveryTime.length()!=9){
                deliveryTime = " "+deliveryTime;
            }
            while(stallDuration.length()!=10){
                stallDuration = " "+stallDuration;
            }
            while(repLevel.length()!=10){
                repLevel = " "+repLevel;
            }
            while(deliveryRate.length()!=9){
                deliveryRate = " "+deliveryRate;
            }
            while(actionRate.length()!=9){
                actionRate = " "+actionRate;
            }
            while(byteSize.length()!=10){
                byteSize = " "+byteSize;
            }
            return segNum+" "+arrivalTime+"\t"+deliveryTime+"\t"+stallDuration+"\t"+repLevel+"\t"+deliveryRate+"\t"+actionRate+"\t"+byteSize+"\t"+bufferLevel;
        }
    }
}
