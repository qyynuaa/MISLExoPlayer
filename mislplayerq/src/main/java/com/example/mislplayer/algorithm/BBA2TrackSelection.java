package com.example.mislplayer.algorithm;

import android.util.Log;
import com.example.mislplayer.FutureSegmentInfos;
import com.example.mislplayer.PlayerActivity;
import com.example.mislplayer.TransitionalAlgorithmListener;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Uses the BBA2 adaptation algorithm to select tracks.
 */
public class BBA2TrackSelection extends AlgorithmTrackSelection {

    /**
     * Creates BBA2TrackSelection instances.
     */
    public static final class Factory implements TrackSelection.Factory {

        private TransitionalAlgorithmListener algorithmListener;

        /**
         * Creates a BBA2TrackSelection factory.
         *
         * @param algorithmListener Provides necessary information to the
         *                          algorithm.
         */
        public Factory(TransitionalAlgorithmListener algorithmListener) {
            this.algorithmListener = algorithmListener;
        }

        @Override
        public BBA2TrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new BBA2TrackSelection(group, tracks, algorithmListener);
        }
    }

    private final TransitionalAlgorithmListener algorithmListener;

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
     */
    public BBA2TrackSelection(TrackGroup group, int[] tracks,
                              TransitionalAlgorithmListener algorithmListener) {
        super(group, tracks);
        this.algorithmListener = algorithmListener;

        selectedIndex = lowestBitrateIndex();
        Log.d(TAG, String.format("Initial selected index = %d", selectedIndex));
        reason = C.SELECTION_REASON_INITIAL;
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        bufferedDurationMs = bufferedDurationUs / 1000;

        int currentSelectedIndex = selectedIndex;
        if (algorithmListener.chunkDataNotAvailable()) {
            selectedIndex = lowestBitrateIndex();
        } else {
            selectedIndex = dash_do_rate_adaptation_bba2();
        }
        Log.d(TAG, String.format("Selected index = %d", selectedIndex));

        if (selectedIndex != currentSelectedIndex) {
            reason = C.SELECTION_REASON_ADAPTIVE;
        }
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

    /* MISL BBA2 adaptation algorithm */
    private int dash_do_rate_adaptation_bba2()
    {
        long total_size = algorithmListener.lastByteSize();
        long bytes_per_sec = total_size / algorithmListener.lastLoadDurationMs();
        lastChunkDurationMs = algorithmListener.lastChunkDurationMs();
        lastChunkIndex = algorithmListener.lastChunkIndex();
        maxBufferMs = algorithmListener.getMaxBufferMs();

        if (total_size == 0 || bytes_per_sec == 0 || lastChunkDurationMs == 0) {
            Log.d(TAG, "[DASH] Downloaded segment  " + total_size + " bytes at " + bytes_per_sec + " bytes per seconds - skipping rate adaptation\n");
            return -1;
        }
        // save the information about segment statistics (kbps)
        Log.d(TAG,"Segment index: " + lastChunkIndex);


        // take the last rate and find its index
        int lastRate = algorithmListener.lastRepLevelKbps();
        int lastRateIndex = PlayerActivity.getRepIndex(lastRate);
        int retVal;
        // set to the lowest rate
        int qRateIndex= lowestBitrateIndex();
        int resevoir = bba1UpdateResevoir(lastRate, lastRateIndex);
        double SFT = (8*total_size)/algorithmListener.lastDeliveryRateKbps();
        if (SFT > lastChunkDurationMs)
            m_staticAlgPar = 1; // switch to BBA1 if buffer is decreasing
        if (bufferedDurationMs < resevoir)               //CHECK BUFFER LEVEL
        {
            if (m_staticAlgPar!=0) {
                retVal = lowestBitrateIndex();
            }
            else
            {
                // start up phase
                if (SFT < 0.125 * lastChunkDurationMs) {
                    // buffer level increasing fast
                    retVal = max(lastRateIndex - 1, 0);
                }
                else {
                    retVal = lastRateIndex; //<<<<<<<??????
                }}

        } else {
            if (m_staticAlgPar!=0)
            {
                // execute BBA1
                retVal = bba1VRAA(lastRateIndex, resevoir);
            }
            else { // beyond resevoir
                int bba1RateIndex = bba1VRAA(lastRateIndex, resevoir);
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
        //dash->wait_bef_send_req = getRequestDelay(dash,group);
        //Log.d("[DASH BBA2]"" dash->wait_bef_send_req:%d\n",dash->wait_bef_send_req));
        //Log.d("[DASH BBA2]" dash->transmittedSegmentsData[%d].requestTime = %d\n",group->download_segment_index+1,dash->transmittedSegmentsData[group->download_segment_index+1].requestTime));
        //   if (lastRateIndex != retVal)
        //gf_dash_set_group_representation(group,gf_list_get(group->adaptation_set->representations,retVal) );

    }


    int bba1UpdateResevoir(int lastRate, int lastRateIndex)
    {
        long resvWin = min(2 * maxBufferMs / lastChunkDurationMs,
                (algorithmListener.mpdDuration()/lastChunkIndex) - lastChunkIndex);
        long avgSegSize = (lastRate * lastChunkDurationMs) / 8; //bytes

        int largeChunks = 0;
        int smallChunks = 0;
        for (int i = 0; i < resvWin; i++)
        {
            if (FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, lastChunkIndex + i, lastRateIndex) > avgSegSize)
                largeChunks+= FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, lastChunkIndex + i, lastRateIndex);
            else
                smallChunks += FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, lastChunkIndex + i, lastRateIndex);

        }
        double resevoir =  8 * ((largeChunks-smallChunks))/(lastRate);
        if (resevoir < (2 * lastChunkDurationMs))
            resevoir = 2 * lastChunkDurationMs;
        else {
            if (resevoir > (0.6 * (maxBufferMs / lastChunkDurationMs) * lastChunkDurationMs))
                resevoir = (0.6 * (maxBufferMs / lastChunkDurationMs) * lastChunkDurationMs);
        }
        return (int)resevoir;
    }

    int bba1VRAA(int lastRateIndex, int resevoir){
        int rateUindex = max(lastRateIndex - 1, 0);
        int rateLindex = min(lastRateIndex + 1, tracks.length);

        int optRateIndex;
        if (bufferedDurationMs < resevoir) {
            optRateIndex = tracks.length; }
        else if (bufferedDurationMs > 0.9 *(maxBufferMs / lastChunkDurationMs) * lastChunkDurationMs)
            optRateIndex = 0;
        else
        {

            int low = lowestBitrate();
            int high = highestBitrate();
            double slope = (high-low)/(0.9 * (maxBufferMs / lastChunkDurationMs) * lastChunkDurationMs - resevoir);
            optRateIndex = getNearestBitrateIndex((low+ slope * (bufferedDurationMs - resevoir))/1000.0);
        }

        if (optRateIndex == tracks.length || optRateIndex == 0)
            return optRateIndex;
        else if (optRateIndex <= rateUindex)
            return optRateIndex;
        else if (optRateIndex >= rateLindex)
            return optRateIndex -1;
        else
            return lastRateIndex;

    }




}
