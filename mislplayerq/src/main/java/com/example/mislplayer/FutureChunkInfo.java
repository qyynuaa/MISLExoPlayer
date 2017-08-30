package com.example.mislplayer;

import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Stores and provides information about chunks in a video.
 */
public class FutureChunkInfo {

    private ArrayList<HashMap<Integer, Integer>> byteSizes;

    @SuppressLint("UseSparseArrays")
    public FutureChunkInfo(int numberOfRepresentations) {
        byteSizes = new ArrayList<>(numberOfRepresentations);
        for (int i = 0; i < numberOfRepresentations; i++) {
            byteSizes.add(new HashMap<Integer, Integer>());
        }
    }

    /**
     * Adds information on a new chunk.
     *
     * @param chunkIndex The index of the chunk within the data stream.
     * @param representationLevel The index of the representation level of
     *        the chunk.
     * @param byteSize The size of the chunk in bytes.
     */
    public void addChunkInfo(int chunkIndex, int representationLevel, int byteSize) {
        byteSizes.get(representationLevel).put(chunkIndex, byteSize);
    }

    /**
     * Gets the size of a chunk in bytes, at a specified representation
     * level.
     *
     * @param chunkIndex The index of the chunk within the data stream.
     * @param representationLevel The index of the representation level of
     *        the chunk.
     * @return The size of the chunk in bytes.
     */
    public int getByteSize(int chunkIndex, int representationLevel){
        return byteSizes.get(representationLevel).get(chunkIndex);
    }

}
