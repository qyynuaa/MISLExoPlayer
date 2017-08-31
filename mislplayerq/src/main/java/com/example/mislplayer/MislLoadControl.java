package com.example.mislplayer;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

/**
 * An alternative to {@link DefaultLoadControl}, which exposes its buffer
 * parameters, and otherwise behaves identically to
 * {@code DefaultLoadControl}.
 */
public class MislLoadControl implements LoadControl {

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

    private LoadControl loadControl;

    private final long minBufferMs;
    private final long maxBufferMs;
    private final long bufferForPlaybackMs;
    private final long bufferForPlaybackAfterRebufferMs;

    /**
     * Creates a new MislLoadControl with default values.
     */
    public MislLoadControl() {
        this(DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        );
    }

    /**
     * Creates a new MislLoadControl.
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
    public MislLoadControl(int minBufferMs, int maxBufferMs,
                           long bufferForPlaybackMs, long bufferForPlaybackAfterRebufferMs) {
        this.loadControl = new DefaultLoadControl(
                new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE), minBufferMs,
                maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs, null
        );
        this.minBufferMs = minBufferMs;
        this.maxBufferMs = maxBufferMs;
        this.bufferForPlaybackMs = bufferForPlaybackMs;
        this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
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

    @Override
    public void onPrepared() {
        loadControl.onPrepared();
    }

    @Override
    public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        loadControl.onTracksSelected(renderers, trackGroups, trackSelections);
    }

    @Override
    public void onStopped() {
        loadControl.onStopped();
    }

    @Override
    public void onReleased() {
        loadControl.onReleased();
    }

    @Override
    public Allocator getAllocator() {
        return loadControl.getAllocator();
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, boolean rebuffering) {
        return loadControl.shouldStartPlayback(bufferedDurationUs, rebuffering);
    }

    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs) {
        return loadControl.shouldContinueLoading(bufferedDurationUs);
    }
}
