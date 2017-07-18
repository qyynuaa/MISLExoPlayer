package com.google.android.exoplayer2.misl;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Provides information to an {@link AdaptationAlgorithm} by listening to transfers,
 * listening to the player, and receiving chunks.
 */
public interface AlgorithmListener extends AlgorithmInfoProvider, ChunkListener,
        TransferListener, ExoPlayer.EventListener {}
