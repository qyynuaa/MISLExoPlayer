package com.example.mislplayer.MISL_Algorithm;

import android.util.Log;
import com.example.mislplayer.FutureSegmentInfos;
import com.example.mislplayer.PlayerActivity;
import com.example.mislplayer.TransitionalAlgorithmListener;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;

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

    private int inc = 0;
    private int m_staticAlgPar = 0;

    private static final String TAG = "BBA2";

    private long bufferedDurationMs;

    private int selectedIndex;
    private int reason;

    /**
     * Creates a BBA2TrackSelection.
     */
    public BBA2TrackSelection(TrackGroup group, int[] tracks,
                              TransitionalAlgorithmListener algorithmListener) {
        super(group, tracks);
        this.algorithmListener = algorithmListener;

        selectedIndex = idealQuality();
        reason = C.SELECTION_REASON_INITIAL;
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        bufferedDurationMs = bufferedDurationUs / 1000;

        int currentSelectedIndex = selectedIndex;
//          Format currentFormat = getSelectedFormat();
        int idealSelectedIndex = adaptiveAlgorithm();
        Format idealFormat = getFormat(idealSelectedIndex);
        selectedIndex = idealSelectedIndex;
        if(selectedIndex!=currentSelectedIndex){
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





    public int adaptiveAlgorithm() { //(TrackGroup chunk, int i)
        if(inc==0 || inc==1){
            inc++;
            return lowestBitrateIndex();
        }
        else {
            inc++;
            return idealQuality();
        }
        // return idealQuality(effectiveNetworkRate,chunk);

    }

    public int idealQuality() {
        if (algorithmListener.chunkDataNotAvailable()) {
            Log.d(TAG, "null log");
            return lowestBitrateIndex();
        } else {
            Log.d(TAG,"launched !");
            return dash_do_rate_adaptation_bba2();
        }
    }

    /* MISL BBA2 adaptation algorithm */
    public int dash_do_rate_adaptation_bba2()
    {

        Double bitrate, time, speed;
        int nb_inter_rep = 0;
        long total_size, bytes_per_sec;
        total_size = algorithmListener.lastByteSize();
        bytes_per_sec = algorithmListener.lastByteSize() / algorithmListener.lastLoadDurationMs();

        if (total_size != 0 && bytes_per_sec != 0 && algorithmListener.lastChunkDurationMs() != 0) {
        } else {
            Log.d(TAG, "[DASH] Downloaded segment  " + total_size + " bytes at " + bytes_per_sec + " bytes per seconds - skipping rate adaptation\n");
            return -1;
        }
        // save the information about segment statistics (kbps)
        Log.d(TAG,"Segment index: "+algorithmListener.lastChunkIndex());


        // take the last rate and find its index
        int lastRate = algorithmListener.lastRepLevelKbps();
        int lastRateIndex = PlayerActivity.getRepIndex(lastRate);
        Log.d(TAG,"last rate index: "+lastRateIndex);
        int retVal;
        // set to the lowest rate
        int qRateIndex= lowestBitrateIndex();
        int resevoir = bba1UpdateResevoir(lastRate, lastRateIndex);
        Log.d(TAG,"m_staticAlgPar: "+m_staticAlgPar);
        long SFT = (8*total_size)/algorithmListener.lastDeliveryRateKbps();
        Log.d(TAG,"SFT2: "+(8*total_size)/algorithmListener.lastDeliveryRateKbps());
        Log.d(TAG,"SFT: "+SFT);
        if (SFT > algorithmListener.lastChunkDurationMs())
            m_staticAlgPar = 1; // switch to BBA1 if buffer is decreasing
        Log.d(TAG,"Before if ");
        if (bufferedDurationMs < resevoir)               //CHECK BUFFER LEVEL
        {
            Log.d(TAG, "buffer_level(=" + bufferedDurationMs + ")< resevoir(="+resevoir+")");
            Log.d(TAG, "m_staticAlgPar"+m_staticAlgPar);
            if (m_staticAlgPar!=0) {
                Log.d(TAG, "m_staticAlgPar is not a zero, returning lowest rate");
                retVal = lowestBitrateIndex();
            }
            else
            {
                Log.d(TAG, "Check SFT against 1/8 of segment duration "+SFT);
                // start up phase
                if (SFT < 0.125 * algorithmListener.lastChunkDurationMs()) {
                    // buffer level increasing fast
                    Log.d(TAG, "buffer level increasing fast:"+SFT);
                    retVal = lastRateIndex - 1 >= 0 ? lastRateIndex - 1 : 0;
                    Log.d(TAG, "increasing fast retval: "+retVal);
                }
                else {
                    retVal = lastRateIndex; //<<<<<<<??????
                    Log.d(TAG, "retval (same as last rate): "+retVal);
                }}

        } else {
            Log.d(TAG,"Greater than reservoir, checking m_staticAlgPar");
            if (m_staticAlgPar!=0)
            {
                // execute BBA1
                Log.d(TAG, "m_staticAlgPar is not zero, calling bba1VRAA"+SFT);
                retVal = bba1VRAA(lastRateIndex, resevoir);
                Log.d(TAG, "After bba1vraa : retVal:"+retVal);
            }
            else { // beyond resevoir
                Log.d(TAG, "beyond reservoir, calling bba1 and bba2");
                int bba1RateIndex = bba1VRAA(lastRateIndex, resevoir);
                Log.d(TAG, "bba1RateIndex:"+bba1RateIndex);
                if (SFT <= 0.5 * algorithmListener.lastChunkDurationMs()) {
                    // buffer level increasing fast
                    qRateIndex = lastRateIndex - 1 >= 0?lastRateIndex - 1 : 0;}
                retVal = bba1RateIndex < qRateIndex? bba1RateIndex:qRateIndex;

                Log.d(TAG, "qRateIndex: "+qRateIndex);
                Log.d(TAG,  "retVal: "+retVal);
                if (bba1RateIndex < qRateIndex) {
                    Log.d(TAG, "Switching to BBA1 completely");
                    m_staticAlgPar = 1;
                }

            }
        }
        Log.d(TAG, "selected quality :"+retVal);
        return retVal;
        //dash->wait_bef_send_req = getRequestDelay(dash,group);
        //Log.d("[DASH BBA2]"" dash->wait_bef_send_req:%d\n",dash->wait_bef_send_req));
        //Log.d("[DASH BBA2]" dash->transmittedSegmentsData[%d].requestTime = %d\n",group->download_segment_index+1,dash->transmittedSegmentsData[group->download_segment_index+1].requestTime));
        //   if (lastRateIndex != retVal)
        //gf_dash_set_group_representation(group,gf_list_get(group->adaptation_set->representations,retVal) );

    }


    int bba1UpdateResevoir(int lastRate, int lastRateIndex)
    {

        int ii = 0;
        int resvWin = (int)(2*(((algorithmListener.getMaxBufferMs()) / algorithmListener.lastChunkDurationMs())) < (algorithmListener.mpdDuration()/algorithmListener.lastChunkIndex()) -(algorithmListener.lastChunkIndex())? 2*(((algorithmListener.getMaxBufferMs()) / algorithmListener.lastChunkDurationMs())): (algorithmListener.mpdDuration()/algorithmListener.lastChunkIndex()) -(algorithmListener.lastChunkIndex()));
        Log.d(TAG, "Last rate: "+lastRate);
        long avgSegSize = (lastRate * algorithmListener.lastChunkDurationMs()) / 8; //bytes
        Log.d(TAG, "avgSize: "+avgSegSize+" resvWin: "+resvWin);
        int largeChunks = 0;
        int smallChunks = 0;
        for (ii=0; ii<resvWin ;ii++)
        {
            if (FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, algorithmListener.lastChunkIndex() + ii, lastRateIndex) > avgSegSize)
                largeChunks+= FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, algorithmListener.lastChunkIndex() + ii, lastRateIndex);
            else
                smallChunks += FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, algorithmListener.lastChunkIndex() + ii, lastRateIndex);

        }
        Log.d(TAG, "smallChunks: "+smallChunks+"largeChunks: "+largeChunks);
        Log.d(TAG, "diff: "+(largeChunks-smallChunks));
        double resevoir =  8 * ((largeChunks-smallChunks))/(lastRate);
        Log.d(TAG, "resevoir: "+resevoir);
        if (resevoir < (2 * algorithmListener.lastChunkDurationMs()))
            resevoir = 2 * algorithmListener.lastChunkDurationMs();
        else if (resevoir > (0.6 * (algorithmListener.getMaxBufferMs() / algorithmListener.lastChunkDurationMs()) * algorithmListener.lastChunkDurationMs()))
            resevoir = (0.6 * (algorithmListener.getMaxBufferMs() / algorithmListener.lastChunkDurationMs()) * algorithmListener.lastChunkDurationMs());
        Log.d(TAG, "resevoir: "+(int)resevoir);
        return (int)resevoir;
    }

    int bba1VRAA(int lastRateIndex, int resevoir){

        int rateUindex = lastRateIndex==0? lastRateIndex:lastRateIndex - 1;
        int rateLindex = lastRateIndex == tracks.length? lastRateIndex : lastRateIndex + 1;
        Log.d(TAG, "rateUindex: "+rateUindex+" rateLindex: "+rateLindex);
        int optRateIndex = 0;
        if (bufferedDurationMs < resevoir) {
            Log.d(TAG, "Calling gf_list_count bufferLevel < reservoir");
            optRateIndex = tracks.length; }
        else if (bufferedDurationMs > 0.9 *(algorithmListener.getMaxBufferMs() / algorithmListener.lastChunkDurationMs()) * algorithmListener.lastChunkDurationMs())
            optRateIndex = 0;
        else
        {

            int low = lowestBitrate();
            int high = highestBitrate();
            double slope = (high-low)/(0.9 * (algorithmListener.getMaxBufferMs() / algorithmListener.lastChunkDurationMs()) * algorithmListener.lastChunkDurationMs() - resevoir);
            Log.d(TAG, "slope: "+slope);
            Log.d(TAG, "argument to findRate: "+(low + slope * (bufferedDurationMs - resevoir))/1000.0);
            optRateIndex = getNearestBitrateIndex((low+ slope * (bufferedDurationMs - resevoir))/1000.0);
            Log.d(TAG, "optRateIndex: "+optRateIndex+" (BBA2)");
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
