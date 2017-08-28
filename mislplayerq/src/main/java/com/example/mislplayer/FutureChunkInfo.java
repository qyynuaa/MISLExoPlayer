package com.example.mislplayer;

import android.annotation.SuppressLint;

import java.util.HashMap;

/**
 * Stores and provides information about chunks in a video.
 */
public class FutureChunkInfo {

    private HashMap<Integer, HashMap<Integer, Integer>> byteSizes;

    @SuppressLint("UseSparseArrays")
    public FutureChunkInfo(int numberOfRepresentations) {
        byteSizes = new HashMap<>(numberOfRepresentations);
    }

    /**
     * Add information on a new chunk.
     *
     * @param chunkIndex The index of the chunk within the data stream.
     * @param representationLevel The representation level of the chunk in
     *        bits per second.
     * @param byteSize The size of the chunk in bytes.
     */
    public void addChunkInfo(int chunkIndex, int representationLevel, int byteSize) {
        byteSizes.get(representationLevel).put(chunkIndex, byteSize);
    }

    /**
     * Get the size of a chunk in bytes, at a specified representation
     * level.
     *
     * @param chunkIndex The index of the chunk within the data stream.
     * @param representationLevel The representation level of the chunk in
     *        bits per second.
     * @return The size of the chunk in bytes.
     */
    public int getByteSize(int chunkIndex, int representationLevel){
        return byteSizes.get(representationLevel).get(chunkIndex);
    }

}
