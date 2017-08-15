package com.example.mislplayer;

import android.os.Handler;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

/**
 * An alternative to {@link DefaultLoadControl},
 * which exposes its buffer parameters.
 */

public class MISLLoadControl implements LoadControl {

    /**
     * The default minimum duration of media that the player will attempt to ensure is buffered at all
     * times, in milliseconds.
     */
    public static final int DEFAULT_MIN_BUFFER_MS = 15000;

    /**
     * The default maximum duration of media that the player will attempt to buffer, in milliseconds.
     */
    public static final int DEFAULT_MAX_BUFFER_MS = 30000;

    /**
     * The default duration of media that must be buffered for playback to start or resume following a
     * user action such as a seek, in milliseconds.
     */
    public static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500;

    /**
     * The default duration of media that must be buffered for playback to resume after a rebuffer,
     * in milliseconds. A rebuffer is defined to be caused by buffer depletion rather than a user
     * action.
     */
    public static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS  = 5000;

    private static final String TAG = "MISLLoadControl";

    private LoadControl loadControl;

    private final long minBufferMs;
    private final long maxBufferMs;
    private final long bufferForPlaybackMs;
    private final long bufferForPlaybackAfterRebufferMs;

    private Handler handler;
    private Runnable restartLoading = new Runnable() {
        @Override
        public void run() {
            timerExpired = true;
            timerRunning = false;
        }
    };
    private boolean timerExpired = true;
    private boolean timerRunning;

    /**
     * Creates a new MISLLoadControl with default values.
     */
    public MISLLoadControl() {
        this(DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        );
    }

    /**
     * Creates a new MISLLoadControl.
     *
     * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
     *     buffered at all times, in milliseconds.
     * @param maxBufferMs The maximum duration of media that the player will attempt buffer, in
     *     milliseconds.
     * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start or
     *     resume following a user action such as a seek, in milliseconds.
     * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered for
     *     playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be caused by
     *     buffer depletion rather than a user action.
     */
    public MISLLoadControl(int minBufferMs, int maxBufferMs,
                           long bufferForPlaybackMs, long bufferForPlaybackAfterRebufferMs) {
        this.loadControl = new DefaultLoadControl(
                new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE), minBufferMs,
                maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs, null
        );
        this.minBufferMs = minBufferMs;
        this.maxBufferMs = maxBufferMs;
        this.bufferForPlaybackMs = bufferForPlaybackMs;
        this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
        handler = new Handler();
    }

    public long getMinBufferMs() {
        return minBufferMs;
    }

    public long getMaxBufferMs() {
        return maxBufferMs;
    }

    public long getBufferForPlaybackMs() {
        return bufferForPlaybackMs;
    }

    public long getBufferForPlaybackAfterRebufferMs() {
        return bufferForPlaybackAfterRebufferMs;
    }

    /**
     * Called by the player when prepared with a new source.
     */
    @Override
    public void onPrepared() {
        loadControl.onPrepared();
    }

    /**
     * Called by the player when a track selection occurs.
     *
     * @param renderers       The renderers.
     * @param trackGroups     The {@link TrackGroup}s from which the selection was made.
     * @param trackSelections The track selections that were made.
     */
    @Override
    public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        loadControl.onTracksSelected(renderers, trackGroups, trackSelections);
    }

    /**
     * Called by the player when stopped.
     */
    @Override
    public void onStopped() {
        loadControl.onStopped();
    }

    /**
     * Called by the player when released.
     */
    @Override
    public void onReleased() {
        loadControl.onReleased();
    }

    /**
     * Returns the {@link Allocator} that should be used to obtain media buffer allocations.
     */
    @Override
    public Allocator getAllocator() {
        return loadControl.getAllocator();
    }

    /**
     * Called by the player to determine whether sufficient media is buffered for playback to be
     * started or resumed.
     *
     * @param bufferedDurationUs The duration of media that's currently buffered.
     * @param rebuffering        Whether the player is rebuffering. A rebuffer is defined to be caused by
     *                           buffer depletion rather than a user action. Hence this parameter is false during initial
     *                           buffering and when buffering as a result of a seek operation.
     * @return Whether playback should be allowed to start or resume.
     */
    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, boolean rebuffering) {
        return loadControl.shouldStartPlayback(bufferedDurationUs, rebuffering);
    }

    /**
     * Called by the player to determine whether it should continue to load the source.
     *
     * @param bufferedDurationUs The duration of media that's currently buffered.
     * @return Whether the loading should continue.
     */
    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs) {
        if (loadControl.shouldContinueLoading(bufferedDurationUs) && timerExpired) {
            if (!timerRunning) {
                handler.postDelayed(restartLoading, 2000);
                timerRunning = true;
            }
            timerExpired = false;
            return true;
        } else {
            return false;
        }
    }
}
