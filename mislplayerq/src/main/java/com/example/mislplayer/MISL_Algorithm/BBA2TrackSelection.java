package com.example.mislplayer.MISL_Algorithm;

import android.util.Log;
import com.example.mislplayer.DashMediaSourceListener;
import com.example.mislplayer.DefaultDashChunkSource2;
import com.example.mislplayer.FutureSegmentInfos;
import com.example.mislplayer.LogSegment;
import com.example.mislplayer.PlayerActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

/**
 * Created by Quentin L on 05/07/2017.
 */

public class BBA2TrackSelection extends BaseTrackSelection {
    private int inc=0;
    public static int m_staticAlgPar = 0;
    public DefaultDashChunkSource2.Factory df= PlayerActivity.df;
    public DefaultLoadControl loadControl=PlayerActivity.loadControl;
    public DashMediaSourceListener dMSL=PlayerActivity.dMSL;
    public static final class Factory implements TrackSelection.Factory {

        private final BandwidthMeter bandwidthMeter;
        private final int maxInitialBitrate;
        private final int minDurationForQualityIncreaseMs;
        private final int maxDurationForQualityDecreaseMs;
        private final int minDurationToRetainAfterDiscardMs;
        private final float bandwidthFraction;
        /**
         * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
         */
        public Factory(BandwidthMeter bandwidthMeter) {
            this (bandwidthMeter, DEFAULT_MAX_INITIAL_BITRATE,
                    DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
                    DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                    DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS, DEFAULT_BANDWIDTH_FRACTION);
        }

        /**
         * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
         * @param maxInitialBitrate The maximum bitrate in bits per second that should be assumed
         *     when a bandwidth estimate is unavailable.
         * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for
         *     the selected track to switch to one of higher quality.
         * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for
         *     the selected track to switch to one of lower quality.
         * @param minDurationToRetainAfterDiscardMs When switching to a track of significantly higher
         *     quality, the selection may indicate that media already buffered at the lower quality can
         *     be discarded to speed up the switch. This is the minimum duration of media that must be
         *     retained at the lower quality.
         * @param bandwidthFraction The fraction of the available bandwidth that the selection should
         *     consider available for use. Setting to a value less than 1 is recommended to account
         *     for inaccuracies in the bandwidth estimator.
         */
        public Factory(BandwidthMeter bandwidthMeter, int maxInitialBitrate,
                       int minDurationForQualityIncreaseMs, int maxDurationForQualityDecreaseMs,
                       int minDurationToRetainAfterDiscardMs, float bandwidthFraction) {
            this.bandwidthMeter = bandwidthMeter;
            this.maxInitialBitrate = maxInitialBitrate;
            this.minDurationForQualityIncreaseMs = minDurationForQualityIncreaseMs;
            this.maxDurationForQualityDecreaseMs = maxDurationForQualityDecreaseMs;
            this.minDurationToRetainAfterDiscardMs = minDurationToRetainAfterDiscardMs;
            this.bandwidthFraction = bandwidthFraction;
        }

        @Override
        public BBA2TrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new BBA2TrackSelection(group, tracks, bandwidthMeter, maxInitialBitrate,
                    minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs,
                    minDurationToRetainAfterDiscardMs, bandwidthFraction);
        }

    }

    public static final int DEFAULT_MAX_INITIAL_BITRATE = 800000;
    public static final int DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10000;
    public static final int DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 25000;
    public static final int DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25000;
    public static final float DEFAULT_BANDWIDTH_FRACTION = 0.75f;

    private final BandwidthMeter bandwidthMeter;
    private final int maxInitialBitrate;
    private final long minDurationForQualityIncreaseUs;
    private final long maxDurationForQualityDecreaseUs;
    private final long minDurationToRetainAfterDiscardUs;
    private final float bandwidthFraction;
    private double networkRate;
    private int selectedIndex;
    private int reason;

    /**
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     empty. May be in any order.
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     */
    public BBA2TrackSelection(TrackGroup group, int[] tracks,
                                 BandwidthMeter bandwidthMeter) {
        this (group, tracks, bandwidthMeter, DEFAULT_MAX_INITIAL_BITRATE,
                DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
                DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS, DEFAULT_BANDWIDTH_FRACTION);
    }

    /**
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     empty. May be in any order.
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     * @param maxInitialBitrate The maximum bitrate in bits per second that should be assumed when a
     *     bandwidth estimate is unavailable.
     * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
     *     selected track to switch to one of higher quality.
     * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
     *     selected track to switch to one of lower quality.
     * @param minDurationToRetainAfterDiscardMs When switching to a track of significantly higher
     *     quality, the selection may indicate that media already buffered at the lower quality can
     *     be discarded to speed up the switch. This is the minimum duration of media that must be
     *     retained at the lower quality.
     * @param bandwidthFraction The fraction of the available bandwidth that the selection should
     *     consider available for use. Setting to a value less than 1 is recommended to account
     *     for inaccuracies in the bandwidth estimator.
     */
    public BBA2TrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter,
                                 int maxInitialBitrate, long minDurationForQualityIncreaseMs,
                                 long maxDurationForQualityDecreaseMs, long minDurationToRetainAfterDiscardMs,
                                 float bandwidthFraction) {
        super(group, tracks);
        this.bandwidthMeter = bandwidthMeter;
        this.maxInitialBitrate = maxInitialBitrate;
        this.minDurationForQualityIncreaseUs = minDurationForQualityIncreaseMs * 1000L;
        this.maxDurationForQualityDecreaseUs = maxDurationForQualityDecreaseMs * 1000L;
        this.minDurationToRetainAfterDiscardUs = minDurationToRetainAfterDiscardMs * 1000L;
        this.bandwidthFraction = bandwidthFraction;
        selectedIndex = idealQuality();
        reason = C.SELECTION_REASON_INITIAL;
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
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
            return PlayerActivity.getRepIndex(df.getLowestBitrate());
        }
        else {
            inc++;
            return idealQuality();
        }
        // return idealQuality(effectiveNetworkRate,chunk);

    }

    public int idealQuality (){ //(double networkRate, TrackGroup group)
        if(DashMediaSourceListener.logSegment!=null) {
            Log.d("BBA2","launched !");
            return dash_do_rate_adaptation_bba2(DashMediaSourceListener.logSegment);
        }
        Log.d("NULLLOG","null log");
        return 0;
    }

    /* MISL BBA2 adaptation algorithm */
    public int dash_do_rate_adaptation_bba2(LogSegment dash)
    {

        Double bitrate, time, speed;
        int nb_inter_rep = 0;
        long total_size, bytes_per_sec;
        total_size = dash.getByteSize();
        bytes_per_sec = dash.getByteSize() / dash.getDeliveryTime();

        if (total_size != 0 && bytes_per_sec != 0 && dash.getSegmentDuration() != 0) {
        } else {
            Log.d("ERROR", "[DASH] Downloaded segment  " + total_size + " bytes at " + bytes_per_sec + " bytes per seconds - skipping rate adaptation\n");
            return -1;
        }
        // save the information about segment statistics (kbps)
        Log.d("[DASH]","Segment index: "+dash.getSegNumber());


        // take the last rate and find its index
        int lastRate = dash.getRepLevel();
        int lastRateIndex = PlayerActivity.getRepIndex(lastRate);
        Log.d("[DASH BBA2]","last rate index: "+lastRateIndex);
        int retVal;
        // set to the lowest rate
        int qRateIndex= PlayerActivity.getRepIndex(df.getLowestBitrate());
        int resevoir = bba1UpdateResevoir(lastRate, lastRateIndex,dash);
        Log.d("[DASH BBA2]","m_staticAlgPar: "+m_staticAlgPar);
        long SFT = (8*total_size)/dash.getDeliveryRate();
        Log.d("[DASH BBA2]","SFT2: "+(8*total_size)/dash.getDeliveryRate());
        Log.d("[DASH BBA2]","SFT: "+SFT);
        if (SFT > dash.getSegmentDuration())
            m_staticAlgPar = 1; // switch to BBA1 if buffer is decreasing
        Log.d("[DASH BBA2]","Before if ");
        if (dash.getBufferLevel() < resevoir)               //CHECK BUFFER LEVEL
        {
            Log.d("[DASH BBA2]", "buffer_level(="+dash.getBufferLevel()+")< resevoir(="+resevoir+")");
            Log.d("[DASH BBA2]", "m_staticAlgPar"+m_staticAlgPar);
            if (m_staticAlgPar!=0) {
                Log.d("[DASH BBA2]","m_staticAlgPar is not a zero, returning lowest rate");
                retVal = PlayerActivity.getRepIndex(df.getLowestBitrate());
            }
            else
            {
                Log.d("[BBA2]","Check SFT against 1/8 of segment duration "+SFT);
                // start up phase
                if (SFT < 0.125 * dash.getSegmentDuration()) {
                    // buffer level increasing fast
                    Log.d("[DASH BBA2]","buffer level increasing fast:"+SFT);
                    retVal = lastRateIndex - 1 >= 0 ? lastRateIndex - 1 : 0;
                    Log.d("[DASH BBA2]","increasing fast retval: "+retVal);
                }
                else {
                    retVal = lastRateIndex; //<<<<<<<??????
                    Log.d("[DASH BBA2]", "retval (same as last rate): "+retVal);
                }}

        } else {
            Log.d("[DASH BBA2]","Greater than reservoir, checking m_staticAlgPar");
            if (m_staticAlgPar!=0)
            {
                // execute BBA1
                Log.d("[DASH BBA2]","m_staticAlgPar is not zero, calling bba1VRAA"+SFT);
                retVal = bba1VRAA(lastRateIndex,resevoir,dash);
                Log.d("[DASH BBA2]","After bba1vraa : retVal:"+retVal);
            }
            else { // beyond resevoir
                Log.d("[DASH BBA2]","beyond reservoir, calling bba1 and bba2");
                int bba1RateIndex = bba1VRAA(lastRateIndex,resevoir,dash);
                Log.d("[DASH BBA2]","bba1RateIndex:"+bba1RateIndex);
                if (SFT <= 0.5 * dash.getSegmentDuration()) {
                    // buffer level increasing fast
                    qRateIndex = lastRateIndex - 1 >= 0?lastRateIndex - 1 : 0;}
                retVal = bba1RateIndex < qRateIndex? bba1RateIndex:qRateIndex;

                Log.d("[DASH BBA2]","qRateIndex: "+qRateIndex);
                Log.d("[DASH BBA2]", "retVal: "+retVal);
                if (bba1RateIndex < qRateIndex) {
                    Log.d("[DASH BBA2]","Switching to BBA1 completely");
                    m_staticAlgPar = 1;
                }

            }
        }
        Log.d("[BBA2]","selected quality :"+retVal);
        return retVal;
        //dash->wait_bef_send_req = getRequestDelay(dash,group);
        //Log.d("[DASH BBA2]"" dash->wait_bef_send_req:%d\n",dash->wait_bef_send_req));
        //Log.d("[DASH BBA2]" dash->transmittedSegmentsData[%d].requestTime = %d\n",group->download_segment_index+1,dash->transmittedSegmentsData[group->download_segment_index+1].requestTime));
        //   if (lastRateIndex != retVal)
        //gf_dash_set_group_representation(group,gf_list_get(group->adaptation_set->representations,retVal) );

    }


    int bba1UpdateResevoir (int lastRate, int lastRateIndex,LogSegment dash)
    {

        int ii = 0;
        int resvWin = (int)(2*(((loadControl.getMaxBufferUs()/1000) / dash.getSegmentDuration())) < (df.getMpdDuration()/dash.getSegNumber()) -(dash.getSegNumber())? 2*(((loadControl.getMaxBufferUs()/1000) / dash.getSegmentDuration())): (df.getMpdDuration()/dash.getSegNumber()) -(dash.getSegNumber()));
        Log.d("[DASH BBA2]","Last rate: "+lastRate);
        long avgSegSize = (lastRate * dash.getSegmentDuration()) / 8; //bytes
        Log.d("[DASH BBA2]","avgSize: "+avgSegSize+" resvWin: "+resvWin);
        int largeChunks = 0;
        int smallChunks = 0;
        for (ii=0; ii<resvWin ;ii++)
        {
            if (FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, dash.getSegNumber() + ii, lastRateIndex) > avgSegSize)
                largeChunks+= FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, dash.getSegNumber() + ii, lastRateIndex);
            else
                smallChunks += FutureSegmentInfos.getByteSize(PlayerActivity.futureSegmentInfos, dash.getSegNumber() + ii, lastRateIndex);

        }
        Log.d("[DASH BBA2]","smallChunks: "+smallChunks+"largeChunks: "+largeChunks);
        Log.d("[DASH BBA2]","diff: "+(largeChunks-smallChunks));
        double resevoir =  8 * ((largeChunks-smallChunks))/(lastRate);
        Log.d("[DASH BBA2]","resevoir: "+resevoir);
        if (resevoir < (2 * dash.getSegmentDuration()))
            resevoir = 2 * dash.getSegmentDuration();
        else if (resevoir > (0.6 * (loadControl.getMaxBufferUs()/1000/ dash.getSegmentDuration()) * dash.getSegmentDuration()))
            resevoir = (0.6 * (loadControl.getMaxBufferUs()/1000/ dash.getSegmentDuration()) * dash.getSegmentDuration());
        Log.d("[DASH BBA2]","resevoir: "+(int)resevoir);
        return (int)resevoir;
    }

    int bba1VRAA(int lastRateIndex, int resevoir,LogSegment dash){

        int rateUindex = lastRateIndex==0? lastRateIndex:lastRateIndex - 1;
        int rateLindex = lastRateIndex==df.trackSelection2.length()? lastRateIndex:lastRateIndex + 1;
        Log.d("[DASH BBA2]","rateUindex: "+rateUindex+" rateLindex: "+rateLindex);
        int optRateIndex = 0;
        if (dash.getBufferLevel() < resevoir) {
            Log.d("[DASH BBA2]","Calling gf_list_count bufferLevel < reservoir");
            optRateIndex = df.trackSelection2.length(); }
        else if (dash.getBufferLevel() > 0.9 *(loadControl.getMaxBufferUs()/1000/dash.getSegmentDuration()) * dash.getSegmentDuration())
            optRateIndex = 0;
        else
        {

            int low = df.getLowestBitrate();
            int high = df.getHighestBitrate();
            double slope = (high-low)/(0.9 * ((loadControl.getMaxBufferUs()/1000) / dash.getSegmentDuration()) * dash.getSegmentDuration() - resevoir);
            Log.d("[DASH BBA2]","slope: "+slope);
            Log.d("[DASH BBA2]","argument to findRate: "+(low + slope * (dash.getBufferLevel()-resevoir))/1000.0);
            optRateIndex = df.getNearestBitrateIndex((low+ slope * (dash.getBufferLevel()-resevoir))/1000.0);
            Log.d("[DASH BBA2]","optRateIndex: "+optRateIndex+" (BBA2)");
        }

        if (optRateIndex == df.trackSelection2.length() || optRateIndex == 0)
            return optRateIndex;
        else if (optRateIndex <= rateUindex)
            return optRateIndex;
        else if (optRateIndex >= rateLindex)
            return optRateIndex -1;
        else
            return lastRateIndex;

    }




}
