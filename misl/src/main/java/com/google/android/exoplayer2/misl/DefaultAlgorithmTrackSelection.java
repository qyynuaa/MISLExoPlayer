package com.google.android.exoplayer2.misl;


import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;


public class DefaultAlgorithmTrackSelection extends BaseTrackSelection
        implements AlgorithmTrackSelection, AlgorithmInfoProvider {

    public static final class Factory implements TrackSelection.Factory {

        private BandwidthMeter bandwidthMeter;

        public Factory(BandwidthMeter bandwidthMeter) {
            this.bandwidthMeter = bandwidthMeter;
        }

        /**
         * Creates a new selection.
         *
         * @param group  The {@link TrackGroup}. Must not be null.
         * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
         *               null or empty. May be in any order.
         * @return The created selection.
         */
        @Override
        public DefaultAlgorithmTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            AdaptationAlgorithm basicAlgorithm = new BasicAdaptationAlgorithm(group, tracks, bandwidthMeter);
            return new DefaultAlgorithmTrackSelection(basicAlgorithm, group, tracks);
        }
    }

    private static final String TAG = "DefaultATS";

    private AdaptationAlgorithm algorithm;
    private MediaChunk lastChunk;
    private long lastArrivalTime;
    private long lastLoadDurationMs;
    private long bufferedDurationUs;
    private int selectedIndex;
    private int reason;

    public DefaultAlgorithmTrackSelection(AdaptationAlgorithm adaptationAlgorithm, TrackGroup group, int[] tracks) {
        super(group, tracks);
        this.algorithm = adaptationAlgorithm;
    }

    /**
     * Returns the index of the selected track.
     */
    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Returns the reason for the current track selection.
     */
    @Override
    public int getSelectionReason() {
        return reason;
    }

    /**
     * Returns optional data associated with the current track selection.
     */
    @Override
    public Object getSelectionData() {
        return null;
    }

    /**
     * Updates the selected track.
     *
     * @param bufferedDurationUs The duration of media currently buffered in microseconds.
     */
    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        logValues(bufferedDurationUs);
        this.bufferedDurationUs = bufferedDurationUs;

        selectedIndex = algorithm.determineIdealIndex(bufferedDurationUs);
        reason = algorithm.getSelectionReason();
    }

    /**
     * Gives the TrackSelection the last chunk that was downloaded, so decisions can be made
     * based on it.
     *
     * <p>Must be called before {@link #updateSelectedTrack} to ensure the information is available
     * when it's needed.
     *
     * @param lastChunk The last chunk that was downloaded.
     */
    @Override
    public void giveLastChunk(MediaChunk lastChunk) {
        this.lastChunk = lastChunk;
    }

    /**
     * Gives the TrackSelection information about the last chunk that was downloaded.
     *
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the load ended.
     * @param loadDurationMs    The duration of the load.
     */
    @Override
    public void giveLastChunkData(long elapsedRealtimeMs, long loadDurationMs) {
        this.lastArrivalTime = elapsedRealtimeMs;
        this.lastLoadDurationMs = loadDurationMs;
    }

    // AlgorithmInfoProvider implementation

    /**
     * The index of the last segment in the stream.
     *
     * @return The last segment's index.
     */
    @Override
    public int lastSegmentNumber() {
        return lastChunk.chunkIndex;
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
        return lastChunk.trackFormat.bitrate;
    }

    /**
     * The delivery rate of the last segment, in bits per second.
     *
     * @return The last segment's delivery rate in bits per second.
     */
    @Override
    public double lastDeliveryRate() {
        return lastChunk.bytesLoaded() * 8E3 / lastLoadDurationMs;
    }

    /**
     * The actual data rate of the last segment, in bits per second.
     *
     * @return The last segment's actual rate in bits per second.
     */
    @Override
    public double lastActualRate() {
        return lastChunk.bytesLoaded() * 8E6 / lastChunk.getDurationUs();
    }

    /**
     * The size of the last segment, in bytes.
     *
     * @return The last segment's size in bytes.
     */
    @Override
    public long lastByteSize() {
        return lastChunk.bytesLoaded();
    }

    // internal methods

    private void logValues(long bufferedDurationUs) {
        if (lastChunk == null) {
            Log.d(TAG, "lastChunk is null.");
        } else {
            Log.d(TAG, String.format("Segment number = %d", lastChunk.chunkIndex));

            Log.d(TAG, String.format("Arrival time = %d", lastArrivalTime));

            Log.d(TAG, String.format("Delivery time/Load duration = %d ms", lastLoadDurationMs));

            Log.d(TAG, String.format("Representation rate = %d bps", lastChunk.trackFormat.bitrate));

            long bitsLoaded = lastChunk.bytesLoaded() * 8;
            final double deliveryRate = bitsLoaded * 1E3 / lastLoadDurationMs;
            Log.d(TAG, String.format("Delivery rate = %g bps", deliveryRate));

            double actualRate = bitsLoaded * 1E6 / lastChunk.getDurationUs();
            Log.d(TAG, String.format("Actual rate = %g bps", actualRate));

            Log.d(TAG, String.format("Byte size = %d", lastChunk.bytesLoaded()));

            Log.d(TAG, String.format("Buffer level = %d µs", bufferedDurationUs));
        }

    }
}
