package com.example.mislplayer;

import android.util.Log;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;


/**
 * Created by Quentin L on 01/06/2017.
 */

public class ExoPlayerListener implements ExoPlayer.EventListener {
    private static long stallBeginningTime=0;
    public static long stallDuration=0;
    public static long flag=-1;
    public void	onLoadingChanged(boolean isLoading){}

    public void	onPlaybackParametersChanged(PlaybackParameters playbackParameters){}

    public void	onPlayerError(ExoPlaybackException error){}

    public void	onPlayerStateChanged(boolean playWhenReady, int playbackState){
        setStallDuration(0);
        if(playbackState==2){
            stallBeginningTime=System.currentTimeMillis();
            Log.d("STDETECT","stalls detected");
        }
        if(playbackState==3){
            flag++;
            setStallDuration(System.currentTimeMillis()-stallBeginningTime);
            Log.d("DUR",stallDuration+"");
            stallBeginningTime=0;
        }
    }
    public static long getStallDuration(){
        return stallDuration;
    }

    public static void setStallDuration(long stallDuration2){
        stallDuration=stallDuration2;
    }
    public void	onPositionDiscontinuity(){}

    public void	onTimelineChanged(Timeline timeline, Object manifest){}

    public void	onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections){}

}
