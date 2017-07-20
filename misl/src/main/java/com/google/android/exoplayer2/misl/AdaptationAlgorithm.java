package com.google.android.exoplayer2.misl;

import com.google.android.exoplayer2.source.TrackGroup;

import java.util.Iterator;

import static java.lang.Math.pow;

/**
 * An algorithm that determines which track an {@link AlgorithmTrackSelection} should select.
 */
public abstract class AdaptationAlgorithm {

    public interface Factory {
        /**
         * Creates a new algorithm.
         *
         * @param group The {@link TrackGroup}.
         * @param tracks The indices of the selected tracks within the {@link TrackGroup}, in any order.
         * @return The created algorithm.
         */
        AdaptationAlgorithm createAlgorithm(TrackGroup group, int... tracks);
    }

    private final static String TAG = "AdaptationAlgorithm";
    private final TrackGroup group;
    private final int[] tracks;

    /**
     * Creates a new algorithm.
     *
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks in group, in order of decreasing bandwidth.
     */
    public AdaptationAlgorithm(TrackGroup group, int[] tracks) {
        this.group = group;
        this.tracks = tracks;
    }

    public TrackGroup getGroup() {
        return group;
    }

    public int[] getTracks() {
        return tracks;
    }

    /**
     * Calculates the index of the TrackGroup track that should be used.
     * @param bufferedDurationUs The duration of media currently buffered in microseconds.
     * @return The index of the TrackGroup track that should be used.
     */
    public abstract int determineIdealIndex(long bufferedDurationUs);

    public abstract int getSelectionReason();

    /**
     * An average calculation.
     */
    public static class Average {

        /**
         * The functionality of a type of average.
         */
        public interface AverageType {
            /**
             * This function is applied to each input value in turn,
             * collecting a cumulative result.
             *
             * @param cumulativeValue The cumulative result so far.
             * @param newElement The next value to process.
             * @return The pre-total average value.
             */
            public double accumulationFunction(double cumulativeValue, double newElement);

            /**
             * This function is applied to the pre-total average value, to give the final value.
             *
             * @param preTotal The result of applying {@link #accumulationFunction} to each
             *                 of the input values.
             * @param numberOfElements The number of input values.
             * @return The average of the input values.
             */
            public double totalFunction(double preTotal, double numberOfElements);
        }

        /**
         * Functionality of different Pythagorean means.
         */
        public enum Pythagorean implements AverageType {
            ARITHMETIC {
                @Override
                public double accumulationFunction(double cumulativeValue, double newElement) {
                    return cumulativeValue + newElement;
                }

                @Override
                public double totalFunction(double preTotal, double numberOfElements) {
                    return preTotal / numberOfElements;
                }
            },
            HARMONIC {
                @Override
                public double accumulationFunction(double cumulativeValue, double newElement) {
                    return cumulativeValue + 1 / newElement;
                }

                @Override
                public double totalFunction(double preTotal, double numberOfElements) {
                    return numberOfElements / preTotal;
                }
            },
            GEOMETRIC {
                @Override
                public double accumulationFunction(double cumulativeValue, double newElement) {
                    return cumulativeValue * newElement;
                }

                @Override
                public double totalFunction(double preTotal, double numberOfElements) {
                    return pow(preTotal, 1 / numberOfElements);
                }
            }
        }

        private final AverageType type;
        private final double[] values;

        /**
         * Creates a new average.
         *
         * @param averageType The type of average to create.
         * @param values The values to average.
         */
        public Average(AverageType averageType, double[] values) {
            this.type = averageType;
            this.values = values;
        }

        /**
         * Calculates the result of the average.
         *
         * @return The value of the average
         */
        public double value() {
            if (values.length == 0) {
                return 0;
            } else {
                double cumulativeValue = values[0];
                if (values.length > 1) {
                    for (int i = 1; i < values.length; i++) {
                        cumulativeValue = type.accumulationFunction(cumulativeValue, values[i]);
                    }
                }
                return type.totalFunction(cumulativeValue, values.length);
            }
        }
    }
}
