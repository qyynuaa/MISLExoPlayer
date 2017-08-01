package com.example.mislplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.mislplayer.algorithm.AdaptationAlgorithm;
import com.example.mislplayer.algorithm.ArbiterAlgorithm;
import com.example.mislplayer.algorithm.BBA2Algorithm;
import com.example.mislplayer.algorithm.BasicAlgorithm;
import com.example.mislplayer.algorithm.ElasticAlgorithm;
import com.example.mislplayer.algorithm.OscarHAlgorithm;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import com.opencsv.CSVReader;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;


public class PlayerActivity extends Activity implements View.OnClickListener,
        ExoPlayer.EventListener, PlaybackControlView.VisibilityListener {

        private static final String TAG = "PlayerActivity";

        private Context userAgent = this;
        private SimpleExoPlayerView playerView;
        private Handler mainHandler;
        private EventLogger eventLogger;
        private SimpleExoPlayer player;
        private int resumeWindow;
        private long resumePosition;
        private DefaultTrackSelector trackSelector;
        private final DefaultBandwidthMeter2 BANDWIDTH_METER = new DefaultBandwidthMeter2(null, null);
        // private final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter(mainHandler,bm);
        private LoadControl loadControl;
        private String videoInfo;
        private int segmentNumber = 0;
        private DashMediaSource videoSource;
        public Thread t;
        public static ArrayList<FutureSegmentInfos> futureSegmentInfos;
        public static ArrayList<Integer> reprLevel;
        public static int beginningIndex;
        private MISLDashChunkSource.Factory df;

        private int minBufferMs = 26000;
        private int maxBufferMs = DEFAULT_MAX_BUFFER_MS;
        private long playbackBufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS;
        private long rebufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
        private AdaptationAlgorithmType algorithmType;
        private AdaptationAlgorithm algorithm;

    @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // To get parameters from previous activity (MainActivity)
            Intent i=getIntent();

            // To choose the right factory for TrackSelection (chooseAlgorithm)
            // AND  useful later for downloaded segments, to know which parameters to store given a specific algorithm,
            algorithmType = (AdaptationAlgorithmType) i.getSerializableExtra("com.example.misl.AlgorithmType");

            mainHandler = new Handler();
            setContentView(R.layout.activity_main);
            View rootView = findViewById(R.id.player_main);
            rootView.setOnClickListener(this);
            playerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
            playerView.setControllerVisibilityListener(this);
            playerView.requestFocus();

        }


        private void initializePlayer() {
            //URL of our MPD file to stream content
            Uri uri = Uri.parse("http://10.0.0.115/~jason_quinlan/x264_4sec/A_New_Hope_16min/DASH_Files/VOD/A_New_Hope_enc_16min_x264_dash.mpd");

            //You can only use another mpd file if you have ITS CSV in raw folder
            // Uri uri = Uri.parse("http://yt-dash-mse-test.commondatastorage.googleapis.com/media/oops-20120802-manifest.mpd");

            algorithm = chooseAlgorithm(algorithmType);

            //Provides instances of DataSource from which streams of data can be read.
            DataSource.Factory dataSourceFactory = buildDataSourceFactory2(algorithm.transferListener());

            //Provides instances of TrackSelection, this will decide which segments we will download later
            TrackSelection.Factory videoTrackSelectionFactory = algorithm.trackSelectionFactory();

            //Will be responsible of choosing right TrackSelections
            trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

            eventLogger = new EventLogger(trackSelector);

            mainHandler = new Handler();

            //Provides instances of DashChunkSource
            df = new MISLDashChunkSource.Factory(dataSourceFactory, algorithm.chunkListener());

            // Our video source media, we give it an URL, and all the stuff before
            videoSource = new DashMediaSource(uri, buildDataSourceFactory2(null), df, mainHandler, eventLogger);

            //Used to play media indefinitely (loop)
            LoopingMediaSource loopingSource = new LoopingMediaSource(videoSource);

            loadControl = new MISLLoadControl(minBufferMs, maxBufferMs,
                    playbackBufferMs, rebufferMs);

            player = ExoPlayerFactory.newSimpleInstance(
                    new DefaultRenderersFactory(this), trackSelector,
                    loadControl);

            if (algorithm.playerListener() != null) {
                player.addListener(algorithm.playerListener());
            }

            //bind the player to a view
            playerView.setPlayer(player);

            // ?
            if (resumeWindow != C.INDEX_UNSET)
                player.seekTo(resumeWindow, resumePosition);

            //prepare the player with the video source
            player.prepare(loopingSource, resumeWindow == C.INDEX_UNSET, false);

            //begin playback
            player.setPlayWhenReady(true);

            //TextView to print out things under player
            TextView debugView = (TextView) findViewById(R.id.debug_text_view);

            //futur segment sizes obtained thanks to CSV file
            futureSegmentInfos = getSegmentSizes();
            if(futureSegmentInfos!=null)
                Log.d(TAG,""+FutureSegmentInfos.getByteSize(futureSegmentInfos,3,getRepIndex(4310)));
            debugView.setTextColor(Color.WHITE);
            debugView.setTextSize(15);

            //Thread to call every 1500 ms a function
            t = new Thread() {
                @Override
                public void run() {
                    try {
                        while (!isInterrupted()) {
                            Thread.sleep(1500);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //getInfosVideo();
                                }
                            });
                        }
                    } catch (InterruptedException e) {
                    }
                }
            };
            t.start();
        }

        //Choose our algorithm given the button selected in the previous Activity
        private AdaptationAlgorithm chooseAlgorithm (
                AdaptationAlgorithmType algorithmType) {
            switch (algorithmType){
                case OSCAR_H:
                    Log.d(TAG,"OSCAR-H has been chosen.");
                    return new OscarHAlgorithm(maxBufferMs);
                case ARBITER:
                    Log.d(TAG,"ARBITER has been chosen.");
                    return new ArbiterAlgorithm(maxBufferMs);
                case BBA2:
                    Log.d(TAG,"BBA2 has been chosen.");
                    return new BBA2Algorithm(maxBufferMs);
                case ELASTIC:
                    Log.d(TAG, "ELASTIC has been chosen.");
                    return new ElasticAlgorithm(maxBufferMs);
                case BASIC_ADAPTIVE:
                    Log.d(TAG,"BASIC_ADAPTIVE has been chosen.");
                default:
                    return new BasicAlgorithm();
            }
        }

        // Here we use our CSV file to obtain all future segment sizes of our media content. Will be used in our algorithms
        public ArrayList<FutureSegmentInfos> getSegmentSizes() {
            try {
                beginningIndex = -1;
                int endIndex = -1;
                InputStream inputStream = getResources().openRawResource(R.raw.segmentbytecostincolumnsanewhopex264); //get the csv file of our video in the raw folder
                CSVReader reader = new CSVReader(new InputStreamReader(inputStream));
                String[] nextLine;
                ArrayList<Integer> repLevel = new ArrayList<>();
                nextLine = reader.readNext(); // a new LINE is read (so every column for this concerned line)
                boolean a = false;
                //We know that our representation levels are on the first line but there may be other informations on this line not interesting for us.
                for (int i = 0; i < nextLine.length; i++) { //nextLine.length == number of columns
                    if (nextLine[i].trim().matches("^-?\\d+$")) {  // check if the value in column i of our line is a value
                        if (!a) {
                            beginningIndex = i;
                            a = true;
                        } // we have to know at which column we can get our first representation level.
                        repLevel.add(Integer.valueOf(nextLine[i].trim())); // add representation level to our Array
                    }
                }
                reprLevel = repLevel;
                endIndex = nextLine.length - 1; // index of the last representation level in the line.
                ArrayList<FutureSegmentInfos> segmentSizes = new ArrayList<FutureSegmentInfos>();// This Array will contain all
                int index = 1;
                int inc = 0;
                while ((nextLine = reader.readNext()) != null) {
                    try {
                        if (Integer.valueOf(nextLine[0].trim()) >= 1) { // This value nextLine[0] (first column of the read line) corresponds to our segment Number,
                            // as 0 is the number of the INIT segment we are not interested in storing it, but all segments after will be stored -> that's why >=1
                            for (int i = 0; i < reprLevel.size(); i++) { //number i will correspond each time to representation level index not its value.
                                FutureSegmentInfos futureSeg = new FutureSegmentInfos(index, i, Integer.valueOf(nextLine[i+2].trim())); // create a future segment info
                                segmentSizes.add(futureSeg); // add it to the array
                                inc++;
                            }
                            index++;
                        }

                    } catch (NumberFormatException n) {
                    }
                }
                return segmentSizes;
            } catch (IOException e) {
                Log.d(TAG, "erreur de lecture fichier");
            }
            return null;
        }

        public int getMaxBufferMs() {
            return maxBufferMs;
        }

        //To get the index of a given representation Level for our media content.
        public static int getRepIndex(int repLevel) {
            for (int i = 0; i < reprLevel.size(); i++) {
                if (reprLevel.get(i) == repLevel) {
                    return i; // the first representation level is not stored in the first column of our first line but at 0+beginningIndex
                }
            }
            return -1;
        }

        private DataSource.Factory buildDataSourceFactory2(TransferListener<? super DataSource> transferListener) {
            return new DefaultDataSourceFactory(this, transferListener, buildHttpDataSourceFactory2(transferListener));
        }

        private HttpDataSource.Factory buildHttpDataSourceFactory2(TransferListener<? super DataSource> transferListener) {
            return new DefaultHttpDataSourceFactory("MyPlayer", transferListener);
        }

        private void updateResumePosition() {

        }

        private void clearResumePosition() {
            resumeWindow = C.INDEX_UNSET;
            resumePosition = C.TIME_UNSET;
        }

        @Override
        public void onNewIntent(Intent intent) {
            releasePlayer();
            clearResumePosition();
            setIntent(intent);
        }

        @Override
        public void onStart() {
            super.onStart();
            if (Util.SDK_INT > 23) {
                initializePlayer();

            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if ((Util.SDK_INT <= 23 || player == null)) {
                initializePlayer();
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if (Util.SDK_INT <= 23) {
                releasePlayer();
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            t.interrupt();
            if (Util.SDK_INT > 23) {
                releasePlayer();
            }
        }

        private HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
            return new DefaultHttpDataSourceFactory(userAgent.toString(), bandwidthMeter); // là aussi jouer avec ça
        }

        private void releasePlayer() {
            if (player != null) {
                updateResumePosition();
                player.release();
                player = null;
                eventLogger = null;
                algorithm.writeLogsToFile();
                algorithm.clearChunkInformation();
            }
        }

        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {
            // Do nothing.
        }

        @Override
        public void onPositionDiscontinuity() {
        }

        @Override
        public void onPlayerError(ExoPlaybackException e) {

        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            // Do nothing.
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }


        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

        }


        @Override
        public void onLoadingChanged(boolean isLoading) {
            // Do nothing.
        }

        @Override
        public void onClick(View view) {

        }

        @Override
        public void onVisibilityChange(int visibility) {

        }

        public SimpleExoPlayer getPlayer() {
            return player;
        }


    }
