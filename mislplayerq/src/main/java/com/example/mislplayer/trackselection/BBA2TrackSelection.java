package com.example.mislplayer.trackselection;

import android.util.Log;

import com.example.mislplayer.PlayerActivity;
import com.example.mislplayer.sampling.SampleProcessor;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Selects adaptive media tracks using the BBA2 algorithm.
 */
public class BBA2TrackSelection extends AlgorithmTrackSelection {

    /**
     * Creates BBA2TrackSelection instances.
     */
    public static final class Factory implements TrackSelection.Factory {

        private SampleProcessor algorithmListener;

        /**
         * Creates a BBA2TrackSelection factory.
         *
         * @param sampleProcessor Provides information about throughput
         *        samples to the algorithm.
         */
        public Factory(SampleProcessor sampleProcessor) {
            this.algorithmListener = sampleProcessor;
        }

        @Override
        public BBA2TrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new BBA2TrackSelection(group, tracks, algorithmListener);
        }
    }

    private int m_staticAlgPar = 0;

    private static final String TAG = "BBA2";

    private long bufferedDurationMs;
    private long lastChunkDurationMs;
    private int lastChunkIndex;
    private long maxBufferMs;

    private int selectedIndex;
    private int reason;

    /**
     * Creates a BBA2TrackSelection.
     *
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}.
     * @param sampleProcessor Provides information about throughput
     *        samples to the algorithm.
     */
    public BBA2TrackSelection(TrackGroup group, int[] tracks,
                              SampleProcessor sampleProcessor) {
        super(group, tracks, sampleProcessor);

        selectedIndex = lowestBitrateIndex();
        Log.d(TAG, String.format("Initial selected index = %d", selectedIndex));
        reason = C.SELECTION_REASON_INITIAL;
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        bufferedDurationMs = bufferedDurationUs / 1000;

        int currentSelectedIndex = selectedIndex;
        if (sampleProcessor.dataNotAvailable()) {
            selectedIndex = lowestBitrateIndex();
        } else if (lastChunkIndex != sampleProcessor.lastChunkIndex()) {
            selectedIndex = calculateSelectedIndex();
            Log.d(TAG, String.format("Selected index = %d", selectedIndex));
        }

        if (selectedIndex != currentSelectedIndex) {
            reason = C.SELECTION_REASON_ADAPTIVE;
        }
    }

    /**
     * Uses the MISL BBA2 adaptation algorithm to find which track
     * should be selected.
     *
     * @return The index of the track which should be selected.
     */
    private int calculateSelectedIndex() {
        lastChunkDurationMs = sampleProcessor.lastChunkDurationMs();
        lastChunkIndex = sampleProcessor.lastChunkIndex();
        maxBufferMs = sampleProcessor.maxBufferMs();

        // save the information about segment statistics (kbps)
        Log.d(TAG, "Segment index: " + lastChunkIndex);


        // take the last rate and find its index
        int lastRate = sampleProcessor.lastRepLevel();
        int lastRateIndex = getRepIndex(lastRate);
        int retVal;
        // set to the lowest rate
        int qRateIndex = lowestBitrateIndex();
        int reservoir = bba1UpdateResevoir(lastRate, lastRateIndex);
        double SFT = sampleProcessor.lastSampleDurationMs();
        if (SFT > lastChunkDurationMs)
            m_staticAlgPar = 1; // switch to BBA1 if buffer is decreasing
        if (bufferedDurationMs < reservoir)               //CHECK BUFFER LEVEL
        {
            if (m_staticAlgPar != 0) {
                retVal = lowestBitrateIndex();
            } else {
                // start up phase
                if (SFT < 0.125 * lastChunkDurationMs) {
                    // buffer level increasing fast
                    retVal = max(lastRateIndex - 1, 0);
                } else {
                    retVal = lastRateIndex;
                }
            }
        } else {
            if (m_staticAlgPar != 0) {
                // execute BBA1
                retVal = bba1VRAA(lastRateIndex, reservoir);
            } else { // beyond reservoir
                int bba1RateIndex = bba1VRAA(lastRateIndex, reservoir);
                if (SFT <= 0.5 * lastChunkDurationMs) {
                    // buffer level increasing fast
                    qRateIndex = max(lastRateIndex - 1, 0);
                }
                retVal = min(bba1RateIndex, qRateIndex);

                if (bba1RateIndex < qRateIndex) {
                    m_staticAlgPar = 1;
                }

            }
        }
        return retVal;
    }

    private int bba1UpdateResevoir(int lastRate, int lastRateIndex) {
        long resvWin = min(2 * maxBufferMs / lastChunkDurationMs,
                (sampleProcessor.mpdDuration() / lastChunkIndex) - lastChunkIndex);
        long avgSegSize = (lastRate * lastChunkDurationMs) / 8000; //bytes

        int largeChunks = 0;
        int smallChunks = 0;
        for (int i = 0; i < resvWin; i++) {
            if (PlayerActivity.futureChunkInfo.getByteSize(lastChunkIndex + i, lastRateIndex) > avgSegSize)
                largeChunks += PlayerActivity.futureChunkInfo.getByteSize(lastChunkIndex + i, lastRateIndex);
            else
                smallChunks += PlayerActivity.futureChunkInfo.getByteSize(lastChunkIndex + i, lastRateIndex);

        }
        double resevoir = 8 * ((largeChunks - smallChunks)) / (lastRate);
        if (resevoir < (2 * lastChunkDurationMs))
            resevoir = 2 * lastChunkDurationMs;
        else {
            if (resevoir > (0.6 * (maxBufferMs / lastChunkDurationMs) * lastChunkDurationMs))
                resevoir = (0.6 * (maxBufferMs / lastChunkDurationMs) * lastChunkDurationMs);
        }
        return (int) resevoir;
    }

    private int bba1VRAA(int lastRateIndex, int resevoir) {
        int rateUindex = max(lastRateIndex - 1, 0);
        int rateLindex = min(lastRateIndex + 1, tracks.length);

        int optRateIndex;
        if (bufferedDurationMs < resevoir) {
            optRateIndex = tracks.length;
        } else if (bufferedDurationMs > 0.9 * (maxBufferMs / lastChunkDurationMs) * lastChunkDurationMs)
            optRateIndex = 0;
        else {

            int low = lowestBitrate();
            int high = highestBitrate();
            double slope = (high - low) / (0.9 * (maxBufferMs / lastChunkDurationMs) * lastChunkDurationMs - resevoir);
            optRateIndex = findBestRateIndex(low + slope * (bufferedDurationMs - resevoir));
        }

        if (optRateIndex == tracks.length || optRateIndex == 0)
            return optRateIndex;
        else if (optRateIndex <= rateUindex)
            return optRateIndex;
        else if (optRateIndex >= rateLindex)
            return optRateIndex - 1;
        else
            return lastRateIndex;
    }

    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
        return reason;
    }

    @Override
    public Object getSelectionData() {
        return null;
    }
}
