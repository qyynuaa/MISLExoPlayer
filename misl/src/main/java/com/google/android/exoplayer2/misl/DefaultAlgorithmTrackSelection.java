package com.google.android.exoplayer2.misl;


import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;


public class DefaultAlgorithmTrackSelection extends BaseTrackSelection implements AlgorithmTrackSelection {

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
            Log.d(TAG, "createTrackSelection called");
            AdaptationAlgorithm basicAlgorithm = new BasicAdaptationAlgorithm(group, tracks, bandwidthMeter);
            return new DefaultAlgorithmTrackSelection(basicAlgorithm, group, tracks);
        }
    }

    private static final String TAG = "DefaultATS";

    private AdaptationAlgorithm algorithm;
    private MediaChunk lastChunk;
    private long lastArrivalTime;
    private long lastLoadDuration;
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
        logValues();

        selectedIndex = algorithm.determineIdealIndex(bufferedDurationUs);
        Log.d(TAG, "Algorithm gave index: " + selectedIndex);
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
        Log.d(TAG, "Last chunk given.");
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
        this.lastLoadDuration = loadDurationMs;
        Log.d(TAG, "Last chunk data given.");
    }


    private void logValues() {
        if (lastChunk == null) {
            Log.d(TAG, "lastChunk is null.");
        } else {
            Log.d(TAG, String.format("lastChunk.chunkIndex = %d", lastChunk.chunkIndex));

            Log.d(TAG, String.format("lastChunk duration = %d", lastChunk.getDurationUs()));

            Log.d(TAG, String.format("lastChunk representation rate = %d", lastChunk.trackFormat.bitrate));

            long bits = lastChunk.bytesLoaded() * 8;
            double actualRate = bits * 1E6 / lastChunk.getDurationUs();
            Log.d(TAG, String.format("lastChunk actual rate = %g", actualRate));

            long calculatedChunkIndex = (lastChunk.endTimeUs) / 4000000;
            Log.d(TAG, String.format("Calculated chunk index is: %d", calculatedChunkIndex));

            Log.d(TAG, String.format("Last chunk's arrival time was: %d", lastArrivalTime));

            Log.d(TAG, String.format("Last chunk's load duration was: %d", lastLoadDuration));
        }

    }
}
