package com.example.mislplayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.mislplayer.logging.DefaultChunkLogger;
import com.example.mislplayer.logging.ManifestListener;
import com.example.mislplayer.sampling.ChunkBasedSampler;
import com.example.mislplayer.sampling.ChunkListener;
import com.example.mislplayer.sampling.DefaultSampleProcessor;
import com.example.mislplayer.sampling.SizeBasedSampler;
import com.example.mislplayer.sampling.TimeBasedSampler;
import com.example.mislplayer.trackselection.Bba2TrackSelection;
import com.example.mislplayer.trackselection.BasicTrackSelection;
import com.example.mislplayer.trackselection.ElasticTrackSelection;
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
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;

import com.opencsv.CSVReader;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static com.google.android.exoplayer2.source.dash.DashMediaSource.DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS;


public class PlayerActivity extends Activity implements View.OnClickListener,
        ExoPlayer.EventListener, PlaybackControlView.VisibilityListener {

    private static final String TAG = "PlayerActivity";
    public static final File DEFAULT_LOG_DIRECTORY
            = new File(Environment.getExternalStorageDirectory().getPath() + "/Logs_Exoplayer");

    private static final int DEBUG_VIEW_UPDATE_MS = 1000;

    private SimpleExoPlayerView playerView;
    private Handler mainHandler;
    private SimpleExoPlayer player;
    private int resumeWindow;
    private long resumePosition;
    private DefaultTrackSelector trackSelector;
    private LoadControl loadControl;
    private DashMediaSource videoSource;
    public static FutureChunkInfo futureChunkInfo;
    public static ArrayList<Integer> reprLevel;
    public static int beginningIndex;
    private MislDashChunkSource.Factory df;

    private AdaptationAlgorithmType algorithmType;

    private TransferListener<? super DataSource> transferListener;
    private ChunkListener chunkListener;
    private TrackSelection.Factory trackSelectionFactory;
    private DefaultChunkLogger chunkLogger;
    private ExoPlayer.EventListener playerListener = null;
    private DefaultSampleProcessor sampleProcessor;
    private ManifestListener manifestListener = new ManifestListener();

    private int minBufferMs = 26000;
    private int maxBufferMs = DEFAULT_MAX_BUFFER_MS;
    private long playbackBufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS;
    private long rebufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;

    private TextView debugView;
    private final StringBuilder debugBuilder = new StringBuilder();
    private final Formatter debugFormatter = new Formatter(debugBuilder);
    private final Runnable debugViewUpdater = new Runnable() {
        @Override
        public void run() {
            updateDebugView();
            mainHandler.postDelayed(debugViewUpdater, DEBUG_VIEW_UPDATE_MS);
        }
    };

    private File chunkLogFile;
    private File sampleLogFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntent();
        algorithmType = (AdaptationAlgorithmType) i.getSerializableExtra("com.example.misl.AlgorithmType");

        mainHandler = new Handler();
        setContentView(R.layout.activity_main);
        View rootView = findViewById(R.id.player_main);
        rootView.setOnClickListener(this);
        playerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
        playerView.setControllerVisibilityListener(this);
        playerView.requestFocus();

        debugView = (TextView) findViewById(R.id.debug_text_view);
    }


    private void initializePlayer() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss", Locale.US);
        Date date = new Date();
        chunkLogFile = new File(DEFAULT_LOG_DIRECTORY, "/" + dateFormat.format(date) + "_Chunk_Log.txt");
        sampleLogFile = new File(DEFAULT_LOG_DIRECTORY, "/" + dateFormat.format(date) + "_Sample_Log.txt");
        configureRun();

        //URL of our MPD file to stream content
        Uri uri = Uri.parse("http://10.0.0.115/~jason_quinlan/x264_4sec/A_New_Hope_16min/DASH_Files/VOD/A_New_Hope_enc_16min_x264_dash.mpd");

        //You can only use another mpd file if you have ITS CSV in raw folder
        // Uri uri = Uri.parse("http://yt-dash-mse-test.commondatastorage.googleapis.com/media/oops-20120802-manifest.mpd");

        //futur segment sizes obtained thanks to CSV file
        futureChunkInfo = getSegmentSizes();

        //Provides instances of DataSource from which streams of data can be read.
        DataSource.Factory mediaDataSourceFactory = buildDataSourceFactory(transferListener);

        //Will be responsible of choosing right TrackSelections
        trackSelector = new DefaultTrackSelector(trackSelectionFactory);

        mainHandler = new Handler();

        chunkLogger = new DefaultChunkLogger(chunkLogFile);

        manifestListener.addListener(chunkLogger);
        manifestListener.addListener(sampleProcessor);

        //Provides instances of DashChunkSource
        df = new MislDashChunkSource.Factory(mediaDataSourceFactory,
                chunkListener);

        // Our video source media, we give it an URL, and all the stuff before
        videoSource = new DashMediaSource(uri,
                buildDataSourceFactory(manifestListener), df,
                Integer.MAX_VALUE,
                DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS,
                mainHandler, chunkLogger);

        //Used to play media indefinitely (loop)
        LoopingMediaSource loopingSource = new LoopingMediaSource(videoSource);

        loadControl = new MislLoadControl(minBufferMs, maxBufferMs,
                playbackBufferMs, rebufferMs);

        player = ExoPlayerFactory.newSimpleInstance(
                new DefaultRenderersFactory(this), trackSelector,
                loadControl);

        player.addListener(sampleProcessor);

        player.addListener(chunkLogger);
        if (playerListener != null) {
            player.addListener(playerListener);
        }

        chunkLogger.setPlayer(player);

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

        debugView.setTextColor(Color.WHITE);
        debugView.setTextSize(15);

        mainHandler.postDelayed(debugViewUpdater, DEBUG_VIEW_UPDATE_MS);
    }

    //Choose our algorithm given the button selected in the previous Activity
    private void configureRun() {
        sampleProcessor = new DefaultSampleProcessor(maxBufferMs, sampleLogFile);

        if (algorithmType == AdaptationAlgorithmType.BASIC_ADAPTIVE) {
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
            transferListener = bandwidthMeter;
            trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        } else if (algorithmType == AdaptationAlgorithmType.BASIC_SIZE) {
            SizeBasedSampler sizeSampler = new SizeBasedSampler(
                    sampleProcessor, 100_000);
            transferListener = sizeSampler;
            trackSelectionFactory = new BasicTrackSelection.Factory(sampleProcessor);
            playerListener = sizeSampler;
        } else if (algorithmType == AdaptationAlgorithmType.BASIC_TIME) {
            TimeBasedSampler timeSampler =
                    new TimeBasedSampler(sampleProcessor);
            transferListener = timeSampler;
            chunkListener = timeSampler;
            trackSelectionFactory = new BasicTrackSelection.Factory(sampleProcessor);
        } else {
            ChunkBasedSampler chunkSampler = new ChunkBasedSampler(sampleProcessor);
            transferListener = chunkSampler;
            chunkListener = chunkSampler;

            switch (algorithmType) {
                case BBA2:
                    Log.d(TAG, "BBA2 has been chosen.");
                    trackSelectionFactory = new Bba2TrackSelection.Factory(sampleProcessor);
                    break;
                case ELASTIC:
                    Log.d(TAG, "ELASTIC has been chosen.");
                    trackSelectionFactory = new ElasticTrackSelection.Factory(sampleProcessor);
                    break;
                default:
                    throw new IllegalArgumentException("Unrecognised algorithm type");
            }
        }
    }

    // Here we use our CSV file to obtain all future segment sizes of our media content. Will be used in our algorithms
    public FutureChunkInfo getSegmentSizes() {
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
            FutureChunkInfo chunkInfo = new FutureChunkInfo(reprLevel.size());
            int index = 1;
            int inc = 0;
            while ((nextLine = reader.readNext()) != null) {
                try {
                    if (Integer.valueOf(nextLine[0].trim()) >= 1) { // This value nextLine[0] (first column of the read line) corresponds to our segment Number,
                        // as 0 is the number of the INIT segment we are not interested in storing it, but all segments after will be stored -> that's why >=1
                        for (int i = 0; i < reprLevel.size(); i++) { //number i will correspond each time to representation level index not its value.
                            chunkInfo.addChunkInfo(index, i, Integer.valueOf(nextLine[i + 2].trim())); // create a future segment info
                            inc++;
                        }
                        index++;
                    }

                } catch (NumberFormatException n) {
                }
            }
            return chunkInfo;
        } catch (IOException e) {
            Log.d(TAG, "erreur de lecture fichier");
        }
        return null;
    }

    public int getMaxBufferMs() {
        return maxBufferMs;
    }

    /**
     * Updates a helper display which displays information about the
     * current state of the player.
     */
    private void updateDebugView() {
        if (player != null) {
            debugBuilder.delete(0, debugBuilder.length());
            if (player.getVideoFormat() != null) {
                debugFormatter.format("Representation ID: %s\n", player.getVideoFormat().id);
                debugFormatter.format("Video bitrate (bps): %d\n", player.getVideoFormat().bitrate);
            }
            if (player.getAudioFormat() != null) {
                debugFormatter.format(" Audio bitrate (bps): %d\n", player.getAudioFormat().bitrate);
            }
            debugFormatter.format("Buffer level (ms): %d\n", player.getBufferedPosition() - player.getCurrentPosition());

            debugView.setText(debugBuilder);
        }
    }

    private DataSource.Factory buildDataSourceFactory(TransferListener<? super DataSource> transferListener) {
        return new DefaultDataSourceFactory(this, transferListener, buildHttpDataSourceFactory(transferListener));
    }

    private HttpDataSource.Factory buildHttpDataSourceFactory(TransferListener<? super DataSource> transferListener) {
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
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            mainHandler.removeCallbacks(debugViewUpdater);

            updateResumePosition();
            player.release();
            player = null;

            chunkLogger.writeLogsToFile();
            chunkLogger.clearChunkInformation();
            sampleProcessor.writeSampleLog();
            sampleProcessor.clearSamples();
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
