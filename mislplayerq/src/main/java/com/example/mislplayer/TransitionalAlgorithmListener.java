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
import java.util.List;
import java.util.ListIterator;

import static com.google.android.exoplayer2.ExoPlayer.STATE_BUFFERING;
import static com.google.android.exoplayer2.ExoPlayer.STATE_READY;
import static java.lang.Math.min;

/**
 * A transitional listener, to be used while migrating code.
 */

public class TransitionalAlgorithmListener implements ChunkListener,
        TransferListener, ExoPlayer.EventListener, SampleProcessor {

    private static final String TAG = "TransitionalAL";
    private static final String LOG_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/Logs_Exoplayer";

    private MediaChunk lastChunk;

    private long arrivalTimeMs;
    private long loadDurationMs;
    private long transferClockMs;

    private long stallClockMs;
    private int lastPlaybackState;
    private long stallDurationMs;

    private long mpdDuration;

    private List<ChunkInformation> downloadedChunkInfo = new ArrayList<>();
    private int maxBufferMs;

    public TransitionalAlgorithmListener(int maxBufferMs) {
        this.maxBufferMs = maxBufferMs;
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

        double deliveryRateKbps = 0;

        if (loadDurationMs > 0) {
            /** The delivery rate of the chunk, in kbps. */
            deliveryRateKbps = byteSize * 8 / loadDurationMs;
            Log.d(TAG, String.format("Load duration = %d ms", loadDurationMs));
            Log.d(TAG, String.format("Delivery rate = %f kbps", deliveryRateKbps));
        }

        ChunkInformation lastChunkInfo = new ChunkInformation(segmentNumber, arrivalTimeMs,
                loadDurationMs, stallDurationMs, representationLevelKbps,
                deliveryRateKbps, actualRatebps, byteSize, 0, segmentDurationMs);

        downloadedChunkInfo.add(lastChunkInfo);
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
    }

    /**
     * Called incrementally during a transfer.
     *
     * @param source           The source performing the transfer.
     * @param bytesTransferred The number of bytes transferred since the previous call to this
     */
    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {}

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    @Override
    public void onTransferEnd(Object source) {
        arrivalTimeMs = SystemClock.elapsedRealtime();
        loadDurationMs = arrivalTimeMs - transferClockMs;
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
        private double deliveryRate;
        private long segmentDuration;

        public ChunkInformation(int segNumber, long arrivalTime, long deliveryTime,
                                long stallDuration, int repLevel, double deliveryRate,
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
        public double getDeliveryRate() {return deliveryRate;}
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

    /** Logs to file data about all the chunks downloaded so far. */
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

    /** Removes all stored chunk information. */
    public void clearChunkInformation() {
        this.downloadedChunkInfo = new ArrayList<>();
    }

    /** Provides information on the most recently-downloaded chunk. */
    private ChunkInformation lastChunkInfo() {
        return downloadedChunkInfo.get(downloadedChunkInfo.size() - 1);
    }

    /** Provides the duration of the current mpd. */
    public long mpdDuration() {
        return mpdDuration;
    }

    /** Gives the current maximum buffer length the player is aiming for. */
    public long maxBufferMs() {
        return maxBufferMs;
    }

    /**
     * Indicates that data on previous chunks is not available.
     *
     * @return true if data on previous chunks is not available, false
     * otherwise.
     */
    public boolean dataNotAvailable() {return downloadedChunkInfo.size() == 0;}

    /** Returns the index of the most recently downloaded chunk. */
    public int lastChunkIndex(){
        return lastChunkInfo().getSegNumber();
    }

    /** Returns the representation level of the most recently downloaded chunk, in kbps. */
    public int lastRepLevelKbps(){
        return lastChunkInfo().getRepLevel();
    }

    /** Returns the size of the most recently downloaded chunk, in bytes. */
    public long lastByteSize(){
        return lastChunkInfo().getByteSize();
    }

    /** Returns the duration of the most recently downloaded chunk, in ms. */
    public long lastChunkDurationMs(){
        return lastChunkInfo().getSegmentDuration();
    }

    /** Returns the actual data rate of the most recently downloaded chunk, in bits per second. */
    public long lastActualRatebps(){
        return lastChunkInfo().getActionRate();
    }

    /** Returns the arrival time of the last chunk, in ms. */
    public long lastArrivalTimeMs(){
        return lastChunkInfo().getArrivalTime();
    }

    /** Returns the amount of time it took to load the last chunk, in ms. */
    public long lastLoadDurationMs(){
        return lastChunkInfo().getDeliveryTime();
    }

    /** Returns the delivery rate of the most recently downloaded chunk, in kbps. */
    public double lastDeliveryRateKbps() {return lastChunkInfo().getDeliveryRate();}

    /**
     * Returns the most recent throughput sample in kbps.
     */
    @Override
    public double lastSampleThroughputKbps() {
        return lastDeliveryRateKbps();
    }

    /**
     * Returns the duration of the most recent throughput sample, in ms.
     */
    @Override
    public long lastSampleDurationMs() {
        return lastLoadDurationMs();
    }

    /**
     * Returns the number of bytes transferred in the last throughput
     * sample.
     */
    @Override
    public long lastSampleBytesTransferred() {
        return lastByteSize();
    }

    /** Returns the total amount of time the player has spent stalling, in ms. */
    public long stallDurationMs(){
        return lastChunkInfo().getStallDuration();
    }

    /**
     * Calculates an appropriate window size, based on the number of
     * downloaded chunks available.
     *
     * @param window The ideal window size.
     * @return The appropriate window size.
     */
    public int getWindowSize(int window) {
        return min(window, downloadedChunkInfo.size());
    }

    /**
     * Provides the load durations of a window of recently downloaded
     * chunks, in ms.
     *
     * <p>If {@code maxWindow} is greater than the number of available chunks,
     * the window will contain only the available chunk load durations.
     *
     * @param maxWindow The maximum size of the window.
     * @return The load durations of the most recently downloaded chunks,
     * in ms.
     */
    public List<Long> getLoadDurationsMs(int maxWindow) {
        ListIterator<ChunkInformation> listIterator =
                downloadedChunkInfo.listIterator(downloadedChunkInfo.size());
        ArrayList<Long> chunkDurations = new ArrayList<>(maxWindow);

        for (int i = 0; i < maxWindow && listIterator.hasPrevious(); i++) {
            ChunkInformation thisChunk = listIterator.previous();
            chunkDurations.add(thisChunk.getDeliveryTime());
        }
        return chunkDurations;
    }

    /**
     * Provides the load durations of a window of recently downloaded
     * chunks, scaled by the total load duration, in ms.
     *
     * <p>If {@code maxWindow} is greater than the number of available chunks,
     * the window will contain only the available chunk load durations.
     *
     * @param maxWindow The maximum size of the window.
     * @return The relative load durations of the most recently downloaded
     * chunks, in ms.
     */
    public List<Double> getRelativeLoadDurationsMs(int maxWindow) {
        double totalLoadDurationMs = getTotalLoadDurationMs(maxWindow);
        ListIterator<ChunkInformation> listIterator =
                downloadedChunkInfo.listIterator(downloadedChunkInfo.size());
        ArrayList<Double> chunkDurations = new ArrayList<>(maxWindow);

        for (int i = 0; i < maxWindow && listIterator.hasPrevious(); i++) {
            ChunkInformation thisChunk = listIterator.previous();
            chunkDurations.add(thisChunk.getDeliveryTime()
                    / totalLoadDurationMs);
        }
        return chunkDurations;
    }

    /**
     * Returns the cumulative load duration of a window of recently
     * downloaded chunks, in ms.
     *
     * <p>If {@code maxWindow} is greater than the number of available chunks,
     * only the available chunks will be considered.
     *
     * @param maxWindow The maximum size of the window.
     * @return The cumulative load duration of the most recently
     * downloaded chunks, in ms.
     */
    public long getTotalLoadDurationMs(int maxWindow) {
        ListIterator<ChunkInformation> listIterator =
                downloadedChunkInfo.listIterator(downloadedChunkInfo.size());
        long totalDuration = 0;

        for (int i = 0; i < maxWindow && listIterator.hasPrevious(); i++) {
            totalDuration += listIterator.previous().getDeliveryTime();
        }

        return totalDuration;
    }

    /**
     * Provides a number of recent throughput samples.
     *
     * @param window The number of throughput samples to provide.
     * @return If the number of throughput samples specified by `window` are
     * available, then that number of samples. If not, then as many as are
     * available.
     */
    private List<Double> getThroughputSamples(int window) {
        int workingWindow = getWindowSize(window);
        int lastIndex = downloadedChunkInfo.size();
        int firstIndex = lastIndex - workingWindow;
        List<ChunkInformation> chunkSublist = downloadedChunkInfo.subList(firstIndex, lastIndex);
        List<Double> rateSamples = new ArrayList<>(workingWindow);

        for (ChunkInformation chunkInfo : chunkSublist) {
            rateSamples.add(chunkInfo.getDeliveryRate());
        }

        return rateSamples;
    }

    /**
     * Finds the minimum of the available throughput samples.
     *
     * @param maxWindow The maximum number of most recent samples to consider.
     * @return The minimum sample in the window.
     */
    public double getMinimumThroughputSample(int maxWindow) {
        double minimumSample = 0;

        for (double thisSample: getThroughputSamples(maxWindow)) {
            if (thisSample < minimumSample) {
                minimumSample = thisSample;
            }
        }

        return minimumSample;
    }

    /**
     * Finds the maximum of the available throughput samples.
     *
     * @param maxWindow The maximum number of most recent samples to consider.
     * @return The maximum sample in the window.
     */
    public double getMaximumThroughputSample(int maxWindow) {
        double maximumSample = 0;

        for (double thisSample: getThroughputSamples(maxWindow)) {
            if (thisSample > maximumSample) {
                maximumSample = thisSample;
            }
        }

        return maximumSample;
    }

    /**
     * Returns an array of the most recent normalised throughput samples.
     *
     * @param maxWindow The maximum number of throughput samples to consider.
     * @return An array of normalised throughput samples.
     */
    public List<Double> getNormalisedSamples(int maxWindow) {
        double maxRate = 0;
        List<Double> samples = getThroughputSamples(maxWindow);
        for (double thisSample : samples) {
            if (thisSample > maxRate) {
                maxRate = thisSample;
            }
        }

        for (int i = 0; i < samples.size(); i++) {
            samples.set(i, samples.get(i) / (maxRate * 1.01));
        }
        return samples;
    }

    /**
     * Calculates a harmonic average of the available throughput samples.
     *
     * @param preferredWindow The number of samples that should be used in
     *                        the calculation, if available.
     * @return If there are the `preferredWindow` number of samples available,
     * the harmonic average of those samples. If not, the harmonic average of
     * the samples that are available.
     */
    public double getSampleHarmonicAverage(int preferredWindow) {
        return new HarmonicAverage(getThroughputSamples(preferredWindow)).value();
    }

    /**
     * Calculates an exponential average of the most recent throughput
     * samples.
     *
     * @param maxWindow The maximum number of recent samples to use in the
     *                  calculation.
     * @param exponentialAverageRatio The ratio to use for the exponential
     *                                average.
     * @return The exponential average of the {@code maxWindow} most
     * recent samples, if that many are available. Otherwise, the
     * exponential average of the available samples.
     */
    public double getSampleExponentialAverage(int maxWindow,
                                              double exponentialAverageRatio) {
        return new ExponentialAverage(getThroughputSamples(maxWindow),
                exponentialAverageRatio).value();
    }

    /**
     * Calculates an exponential variance of the most recent throughput
     * samples.
     *
     * @param sampleAverage The exponential average of the most recent
     *                      throughput samples.
     * @param maxWindow The maximum number of recent samples to use in the
     *                  calculation.
     * @param exponentialVarianceRatio The ratio to use for the exponential
     *                                variance.
     * @return The exponential variance of the {@code maxWindow} most
     * recent samples, if that many are available. Otherwise, the
     * exponential variance of the available samples.
     */
    public double getSampleExponentialVariance(double sampleAverage,
                                               int maxWindow,
                                               double exponentialVarianceRatio) {
        return new ExponentialVariance(sampleAverage,
                getThroughputSamples(maxWindow),
                exponentialVarianceRatio).value();
    }

    public double[] oscarKumarParEstimation(int estWindow, double expAvgRatio) {
        int window = getWindowSize(estWindow);
        List<Double> rateSamples = getThroughputSamples(window);
        double maxRate=rateSamples.get(0);
        for (int i = 1; i < window; i++) {
            if (rateSamples.get(i) > maxRate)
                maxRate = rateSamples.get(i);
        }
        double normalizedSamples[] = new double[window];
        for (int i = 0; i < window; i++) {
            normalizedSamples[i] = rateSamples.get(i) / maxRate / 1.01;

        }
        // estimate the weights of different samples
        double weights[] = new double[window];
        double freshWeights[] = new double[window];
        double durationWeight[] = new double[window];
        double weightSum = (1 - Math.pow(1 - expAvgRatio, window));
        double totalDuration = 0;
        double relWght = 1;
        for (int i = 0; i < window; i++) {
            freshWeights[i] = (expAvgRatio) * Math.pow(1 - expAvgRatio, i) / weightSum;
            durationWeight[i] = lastLoadDurationMs();
            //(dash->transmittedSegmentsData[group->download_segment_index -i].receiveTime - dash->transmittedSegmentsData[group->download_segment_index -i].requestTime);
            totalDuration += durationWeight[i];
        }
        for (int i = 0; i < window; i++) {
            durationWeight[i] = durationWeight[i] / totalDuration;
            weights[i] = relWght * freshWeights[i] + (1 - relWght) * durationWeight[i];
        }
        // Calculate the average and variance
        double avgRate = 0;
        for (int i = 0; i < window; i++) {
            avgRate += weights[i] * normalizedSamples[i];
        }
        // avgRate =  OSCAR_KUM_REDUCTION * avgRate;
        double rateVar = 0;
        for (int i = 0; i < window; i++) {
            rateVar += weights[i] * Math.pow(normalizedSamples[i] - avgRate, 2);
        }
        rateVar = window * rateVar / (window - 1);

        double checkTerm = avgRate * (1 - avgRate) / rateVar;

        // estimate beta parameters for the initial guess
        double beta1 = 0;
        //double beta2 = 0;
        if (checkTerm > 1) {
            beta1 = avgRate * (checkTerm - 1);

        } else {
            beta1 = 1;

        }
        double kum1Min = beta1;
        double smin = computeS(window, normalizedSamples, weights, kum1Min);
        while (smin < 0 && kum1Min > 0.0001) {
            kum1Min = kum1Min / 2;
            smin = computeS(window, normalizedSamples, weights, kum1Min);
        }
        double kum1Max = beta1;
        double smax = computeS(window, normalizedSamples, weights, kum1Max);
        while (smax > 0 && kum1Max < 2000) {
            kum1Max = kum1Max * 2;
            smax = computeS(window, normalizedSamples, weights, kum1Max);
        }

        double kum1 = (kum1Max + kum1Min) / 2;
        double smid = 0;
        while (kum1Max - kum1Min > 0.001) {
            kum1 = (kum1Max + kum1Min) / 2;
            smid = computeS(window, normalizedSamples, weights, kum1);
            if (smid > 0)
                kum1Min = kum1;
            else
                kum1Max = kum1;
        }

        double kum2 = 0;
        for (int i = 0; i < window; i++) {
            kum2 += weights[i] * Math.log(1 - Math.pow(normalizedSamples[i], kum1));
        }

        if (kum2 == 0)
            kum2 = 1e-5;
        else {
            kum2 = -1 / kum2;
            kum2 = kum2 > 1e5 ? 1e5 : kum2;
        }
        return new double[] {kum1, kum2, 0, maxRate};
    }

    private double computeS(int window, double[] samples, double[] weights, double kum1) {
        double T1 = 0;
        double T2 = 0;
        double T3 = 0;
        double y, yCompl;

        for (int i = 0; i < window; i++) {

            y = Math.pow(samples[i], kum1);
            yCompl = 1 - y;
            T1 += weights[i] * Math.log(y) / (yCompl);
            T2 += weights[i] * Math.log(y) / (yCompl) * y;
            T3 += weights[i] * Math.log(yCompl);
        }

        return (1 + T1 + T2 / T3);
    }

    /**
     * A pythagorean arithmetic average.
     */
    public class ArithmeticAverage {

        private int window;
        private double[] rates;

        public ArithmeticAverage(int window, double[] rates) {
            this.window = window;
            this.rates = rates;
        }

        public double value() {
            double subTotal = 0;
            for (int i = 0; i < window; i++) {
                subTotal += rates[i];
            }
            return subTotal / window;
        }
    }

    public class ArithmeticVariance {

        private int window;
        private double averageRate;
        private double[] rates;

        public ArithmeticVariance(int window, double averageRate, double[] rates) {
            this.window = window;
            this.averageRate = averageRate;
            this.rates = rates;
        }

        public double value() {
            double totalDeviation = 0;
            double result = 0;

            for (int i = 0; i < window; i++) {
                totalDeviation += Math.pow(averageRate - rates[i], 2);
            }
            if (window > 1) {
                result = totalDeviation / (window - 1);
            }

            return result;
        }
    }

    /**
     * A pythagorean harmonic average.
     */
    public class HarmonicAverage {

        private int window;
        private List<Double> rates;

        public HarmonicAverage(List<Double> samples) {
            this(samples.size(), samples);
        }

        public HarmonicAverage(int window, List<Double> rates) {
            this.window = window;
            this.rates = rates;
        }

        public double value() {
            double subTotal = 0;
            for (int i = 0; i < window; i++) {
                subTotal += 1 / rates.get(i);
            }
            return window / subTotal;
        }
    }

    public class HarmonicVariance {

        private int window;
        private double[] rates;

        public HarmonicVariance(int window, double[] rates) {
            this.window = window;
            this.rates = rates;
        }

        public double value() {
            double rateRcp[] = new double[window];
            double reducedAvg[] = new double[window];
            double rateRcpSum = 0;
            double reducedAvged = 0;
            double totalDeviation = 0;

            for (int i = 0; i < window; i++) {
                rateRcp[i] += 1 / rates[i];
                rateRcpSum += rateRcp[i];
            }
            for (int i = 0; i < window; i++) {
                reducedAvg[i] = (window - 1) / (rateRcpSum - rateRcp[i]);
                reducedAvged += reducedAvg[i];
            }
            reducedAvged = reducedAvged / window;
            for (int i = 0; i < window; i++) {
                totalDeviation += Math.pow(reducedAvged - reducedAvg[i], 2);
            }
            return (1 - 1 / window) * totalDeviation;
        }
    }

    /**
     * An exponential average.
     */
    public class ExponentialAverage {

        private List<Double> rates;
        private double ratio;

        public ExponentialAverage(List<Double> rates, double ratio) {
            this.rates = rates;
            this.ratio = ratio;
        }

        public double value() {
            double weightSum = (1 - Math.pow(1 - ratio, rates.size()));
            double subTotal = 0;

            for (int i = 0; i < rates.size(); i++) {
                double thisWeight = ratio * Math.pow(1 - ratio, i) / weightSum;
                subTotal += thisWeight * rates.get(i);
            }
            return subTotal;
        }
    }

    public class ExponentialVariance {

        private double averageRate;
        private List<Double> rates;
        private double ratio;

        public ExponentialVariance(double averageRate, List<Double> rates, double ratio) {
            this.averageRate = averageRate;
            this.rates = rates;
            this.ratio = ratio;
        }

        public double value() {
            double weightSum = (1 - Math.pow(1 - ratio, rates.size()));
            double totalDeviation = 0;

            for (int i = 0; i < rates.size(); i++) {
                double thisWeight = (ratio) * Math.pow(1 - ratio, i) / weightSum;
                totalDeviation += thisWeight * Math.pow(averageRate - rates.get(i), 2);
            }
            return rates.size() * totalDeviation / (rates.size() - 1);
        }
    }
}
