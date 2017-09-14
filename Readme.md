# Intro

This repository shows the work I did in July and August 2017, while working as a summer intern at MISL in UCC.

Note that I have had to remove some things from the project history, as we were working on a couple of adaptation algorithms that have not yet been published.

# The Work

I took over from another intern, and was responsible for implementing and testing video adaptation algorithms (for use with DASH streams) using [ExoPlayer](https://google.github.io/ExoPlayer/ "The ExoPlayer project homepage.").

## Algorithms

Three adaptation algorithms had been implemented by the intern I took over from – two in-house algorithms and one well-known one, for comparison.

I implemented two further algorithms – one more in-house algorithm, and one more well-known one. I also laid some of the groundwork for the implementation of another well-known algorithm (by figuring out how to implement customised request scheduling for video chunks).

## Throughput Sampling

### Sampling Source

Originally, we were registering an `AdaptiveMediaSourceEventListener` on the `DashMediaSource` to obtain throughput samples for each video chunk.

I noticed that this listener was being notified asynchronously, and as a result sometimes algorithm decisions were made without the most recent info available (even though a chunk had downloaded, the algorithms hadn’t yet received the information about that chunk).

I worked out how to retrieve this information by using a custom implementation of an ExoPlayer component interface that delegated to existing implementations and added extra functionality (following the decorator pattern).

Unfortunately, this method meant that it was not possible for us to use HLS, as the equivalent component for HLS was implemented as a package-private final class, so it could not be subclassed or replaced with a custom implementation. This proved a difficulty for our collaboration with AT&T, and will probably have to be solved by modifying the ExoPlayer library itself, rather than importing it unmodified, as we were aiming to do.

### Sampling Methods

When I began working at MISL, the existing code was able to sample the throughput on a chunk-by-chunk basis.

With direction from my supervisor, I changed the structure of the code to separate sample collection (where each sample recorded the throughput for a given time period, along with the number of bytes transferred and time taken) from sample processing (calculation of averages, variances, and other statistical measures) from track selection.

With some exceptions for algorithms that only made sense when sampling on a chunk-by-chunk basis, this allowed different algorithms to be used interchangeably with different methods of sampling (e.g. time-based sampling, where a throughput sample is recorded every x ms, or size-based sampling, where a throughput sample is recorded every x bytes transferred).

## Logging

The intern I took over from had implemented a logging structure which created logs according to a tabular, white-space separated format, with one line for each chunk downloaded.

I separated the logging into its own class, and made it more flexible by applying the builder pattern, which should allow new logging formats (e.g. xml-based formats) to be implemented more easily and with minor changes to existing code.

## Project Repository

I created a git repository for the project and maintained it while I was working at MISL. I improved my git style (branch use, atomic commits, clear and standardised commit messages) over the 2 months, and I’m happy with the repository I left behind for those taking over the work – I think it will be clear and helpful, providing context for the decisions that were made.
