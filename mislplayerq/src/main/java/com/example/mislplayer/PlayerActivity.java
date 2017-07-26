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
import com.example.mislplayer.MISL_Algorithm.ArbiterTrackSelection;
import com.example.mislplayer.MISL_Algorithm.BBA2TrackSelection;
import com.example.mislplayer.MISL_Algorithm.DASHTrackSelection;
import com.example.mislplayer.MISL_Algorithm.ElasticTrackSelection;
import com.example.mislplayer.MISL_Algorithm.OscarHTrackSelection;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
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
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;


public class PlayerActivity extends Activity implements View.OnClickListener,ExoPlayer.EventListener, PlaybackControlView.VisibilityListener {


        private Context userAgent = this;
        private SimpleExoPlayerView playerView;
        private Handler mainHandler;
        private EventLogger eventLogger;
        private SimpleExoPlayer player;
        private int resumeWindow;
        private long resumePosition;
        private DefaultTrackSelector trackSelector;
        private BandwidthMeterEventListener bm = new BandwidthMeterEventListener();
        private final DefaultBandwidthMeter2 BANDWIDTH_METER = new DefaultBandwidthMeter2(mainHandler, bm);
        // private final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter(mainHandler,bm);
        private DefaultLoadControl loadControl;
        private String videoInfo;
        private int segmentNumber = 0;
        private DashMediaSource videoSource;
        private final TransitionalAlgorithmListener algorithmListener = new TransitionalAlgorithmListener(this);
        public Thread t;
        public static ArrayList<FutureSegmentInfos> futureSegmentInfos;
        public static ArrayList<Integer> reprLevel;
        public static int beginningIndex;
        private MISLDashChunkSource.Factory df;
        public static String ALGORITHM_TYPE;

        private int minBufferMs = DEFAULT_MIN_BUFFER_MS;
        private int maxBufferMs = DEFAULT_MAX_BUFFER_MS;
        private long playbackBufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS;
        private long rebufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // To get parameters from previous activity (MainActivity)
            Intent i=getIntent();

            // To choose the right factory for TrackSelection (chooseAlgorithm)
            // AND  useful later for downloaded segments, to know which parameters to store given a specific algorithm,
            ALGORITHM_TYPE=i.getStringExtra("ALGORITHM TYPE");

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

            //Provides instances of DataSource from which streams of data can be read.
            DataSource.Factory dataSourceFactory = buildDataSourceFactory2(algorithmListener);

            //Provides instances of TrackSelection, this will decide which segments we will download later
            TrackSelection.Factory videoTrackSelectionFactory = chooseAlgorithm(ALGORITHM_TYPE);

            //Will be responsible of choosing right TrackSelections
            trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

            eventLogger = new EventLogger(trackSelector);

            mainHandler = new Handler();

            //Provides instances of DashChunkSource
            df = new MISLDashChunkSource.Factory(dataSourceFactory, algorithmListener);

            // Our video source media, we give it an URL, and all the stuff before
            videoSource = new DashMediaSource(uri, buildDataSourceFactory2(null), df, mainHandler, eventLogger);

            //Used to play media indefinitely (loop)
            LoopingMediaSource loopingSource = new LoopingMediaSource(videoSource);

            Context context = this;

            //If you want to modify buffer parameters use this
            DefaultAllocator allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);

            minBufferMs = 26000;
            loadControl = new DefaultLoadControl(allocator, minBufferMs,
                    maxBufferMs, playbackBufferMs, rebufferMs);

            //Creates an instance of our player, we have to give it all previous stuff
            player = ExoPlayerFactory.newSimpleInstance(context, trackSelector, loadControl);

            //bind the player to a view
            playerView.setPlayer(player);

            //Listen to the player behavior to get some infos about it
            ExoPlayerListener exoPlayerListener = new ExoPlayerListener();
            player.addListener(exoPlayerListener);

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
                Log.d("FUTSEG",""+FutureSegmentInfos.getByteSize(futureSegmentInfos,3,getRepIndex(4310)));
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
        public TrackSelection.Factory chooseAlgorithm (String name){
            switch (name){
                case "BASIC_EXOPLAYER":
                    Log.d("NOTE","BASIC_EXOPLAYER has been chosen.");
                    return new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
                case "BASIC_ADAPTIVE":
                    Log.d("NOTE","BASIC_ADAPTIVE has been chosen.");
                    return new DASHTrackSelection.Factory(BANDWIDTH_METER);
                case "OSCAR-H":
                    Log.d("NOTE","OSCAR-H has been chosen.");
                    return new OscarHTrackSelection.Factory(algorithmListener);
                case "ARBITER":
                    Log.d("NOTE","ARBITER has been chosen.");
                    return new ArbiterTrackSelection.Factory(algorithmListener);
                case "BBA2":
                    Log.d("NOTE","BBA2 has been chosen.");
                    return new BBA2TrackSelection.Factory(algorithmListener);
                case "ELASTIC":
                    Log.d("NOTE", "ELASTIC has been chosen.");
                    return new ElasticTrackSelection.Factory(algorithmListener);
            }
            Log.d("ERROR","ALGORITHM NOT FOUND");
            return new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
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
                                Log.d("STORED","index: "+index+" repIndex: "+i+" value: "+Integer.valueOf(nextLine[i+2].trim()));
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
                Log.d("ERREUR", "erreur de lecture fichier");
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

        private void getInfosVideo() {
            TextView debugView = (TextView) findViewById(R.id.debug_text_view);
        /*String videoInfo2 = "";
        if (player.getVideoFormat() != null)
            videoInfo2 = "Segment " + segmentNumber + " -> Height : " + player.getVideoFormat().height + " Width : " + player.getVideoFormat().width + "\n";
        if (!videoInfo2.equals(videoInfo))
            segmentNumber++;
        videoInfo = "Segment " + segmentNumber + " -> Height : " + player.getVideoFormat().height + " Width : " + player.getVideoFormat().width + "\n";
        String buffer = "Buffer percentage : " + player.getBufferedPercentage() + "% \n";
        String trackgroups = "Number of Trackgroups : " + player.getCurrentTrackGroups().length + "\n";
        String period = "Period index : " + player.getCurrentPeriodIndex() + "\n";
        String videoID = "Representation ID : " + player.getVideoFormat().id + "\n";
        String videoBitrate = "Video bitrate : " + player.getVideoFormat().bitrate;
        String audioBitrate = " Audio bitrate : " + player.getAudioFormat().bitrate + "\n";
        //String sth = "\nMapped Track Info : "+trackSelector.getCurrentMappedTrackInfo().getTrackGroups(0).get(0).getFormat(0).toString();
        String bandwidth = "Bandwidth : " + (BANDWIDTH_METER.getBitrateEstimate()) / 8000 + " ko/s \n";
        String bytesAllocated = "Total bytes allocated : " + loadControl.getAllocator().getTotalBytesAllocated() + "\n";
        String bufferedPosition = "Buffer Level : " + player.getBufferedPosition() + "\n";
       // debugView.setText(videoInfo + buffer + trackgroups + period + videoID + videoBitrate + audioBitrate + bandwidth + bytesAllocated + bufferedPosition);
        */
            if(algorithmListener.logSegment!=null) {
                String test = "SEG NUMBER : " + algorithmListener.logSegment.getSegNumber();
                debugView.setText(test);
            }
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
            LogSegment.writeLogSegInFile(algorithmListener.getSegInfos(), BANDWIDTH_METER.getSampleBytesCollected());
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




