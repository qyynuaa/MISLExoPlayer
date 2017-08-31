# Intro

This readme contains the content of a presentation I gave before I left, summarising the work so far.

I've left it here in case it might be of some help to whoever is next working on this code – feel free to modify/delete it however you want!

You can always email me if there are any questions: noelfbourke@gmail.com

# Describing ExoPlayer

## Overview

* each player is an object that implements the interface `ExoPlayer`, and is made up of various components

    * tasks are delegated to different components

    * you can inject components when you create the player to customise behaviour

    * components are generally made up of sub-components, which can also be customised by injection

## Track Selections

These are responsible for specifying which tracks should be loaded.

* Have a method `updateSelectedTrack()` which can be called to allow adaptation.

    * In DASH this is called by `DefaultDashChunkSource` in `getNextChunk()`, before the chunk is requested

    * For SmoothStreaming it's pretty much identical – called by `DefaultSsChunkSource` in `getNextChunk()`

    * HLS is a bit different, but it's still in `HlsChunkSource` in `getNextChunk()`

A track selection is created for each renderer, if suitable TrackGroups are available (e.g. the `DefaultTrackSelector` uses one video renderer, one audio renderer, one text renderer, and one metadata renderer, but we're only giving it video track groups, so it only makes a track selection for the video renderer).

When using the `DefaultTrackSelector`, the track selection for the video tracks is created from a `TrackSelection.Factory` instance that's passed in when the `DefaultTrackSelector` is created.

* `getNextChunk()` is called twice for some chunks by the `ChunkSampleStream` and so on back the chain – I can't tell why

    * maybe some problem with the system we're using or a bug in exoplayer?

## Bandwidth Meters

These are used by ExoPlayer for measuring throughput. There's a `BandwidthMeter` interface, which defines the method `getBitrateEstimate()`, and there's a `DefaultBandwidthMeter`, which provides a default implementation of this interface.

* The default implementation produces a sample at the end of every transfer, and uses a sliding percentile to calculate an estimate from these samples.

## `TransferListener`

The `DefaultBandwidthMeter` implements the `TransferListener` interface, which allows it to be registered on a data source and be notified of the transfers that happen.

    * This means it gets notified of the start and end of each transfer, as well as periodically notified during a transfer, when bytes have been transferred

    * It's possible to differentiate between sources by object equality, but I'm not sure if different streams will *always* have different sources

        * In practice, audio and video transfers have had different sources, so it's possible to distinguish them (but not tell which is which, unless you know the sources ahead of time)

## `AdaptiveMediaSourceEventListener`

This interface is used for logging – allows classes to be registered as listeners on the default media sources, and be notified when loads are started, completed, cancelled, and run into errors.

* Though it provides useful info, according to the developers it shouldn't be used to alter the behaviour of the player. I think this is because the event dispatcher is used to schedule calls to the listeners' methods, meaning it isn't guaranteed to happen in time (I have seen this happening, too)

## The `LoadControl`

This controls when the loading of chunks from the media source should happen.

* The method `shouldContinueLoading()` is called by the player to determine whether it should continue loading

    * I *believe* this method call is initiated by `ChunkSampleStream` each time a load completes or is cancelled, through `callback.onContinueLoadingRequested()` – it's a little hard to tell, but it looks like this propagates through `DashMediaPeriod` and to `ExoPlayerImplInternal`, which calls `shouldContinueLoading()`

## The `ChunkSource`

This provides `Chunks` for a `ChunkSampleStream` to load.

* This is used for DASH and SmoothStreaming, but not HLS

# What We've Done

## Logging

* created a ChunkLogger interface and implementing class DefaultChunkLogger

    * the class acts as an AdaptiveMediaSourceEventListener and an ExoPlayer.EventListener

    * it also receives the manifest request time from a ManifestListener class, which is registered as a TransferListener on the manifest DataSource.Factory passed to the DashMediaSource

        * This will not work for HLS, as the manifest is requested differently

## Track Selection

This package is where the algorithms are implemented, each as a different track selection class. There is a common base class `AlgorithmTrackSelection` which extends `BaseTrackSelection` in the ExoPlayer core library, and each algorithm class extends `AlgorithmTrackSelection`.

Most algorithms take a reference to a SampleProcessor implementation, which provides information to the algorithm to assist in bandwidth estimation (e.g. averages of recent throughput samples).

[Talk about which algorithms we've implemented]

## Sampling

This package covers collection and processing of throughput samples.

Collection of samples can be on a chunk-by-chunk basis, or based on time or size.

For chunk-based sampling we're also getting the last chunk from the MISLDashChunkSource each time a new chunk is requested (before the track selection is done) – this provides extra information not available under the TransferListener interface.

## Future Chunk Info

At the moment we're reading information about future chunks from a CSV file – this can be used to (for example) check the average quality rate of the next few chunks, rather than depending on the advertised average quality rate.

# What We Haven't Done

* Implemented Festive

## Logging

* Store log entries by their media start time (using a map or sorted map).

    * This would avoid our chunk index calculation, and work across DASH and HLS.

* Track chunk replacement, etc. in the log

    * Cancelled downloads

* Log format

    * At the moment we're using a whitespace-separated table, which is a little frustrating (for example, changing column titles requires changing the column sizes)

    * It might be good to make the log format more flexible/configurable (builder pattern maybe?)

## Future Chunk Info

* Maybe use XML format instead of CSV?

### Downloading Metadata

* Rather than using a local file for the segment sizes, you'd download an XML file which contains metadata

    * This includes segment sizes but also other things, e.g. quality score (to allow quality-based adaptation)

        * If a particular frame is unimportant (e.g. a black screen), requesting a lower representation rate for that would not have a strong impact on user experience and so might be advantageous to minimise transmitted data – this could be reflected in a quality score

* We could assume the file will be available at the same address as the mpd but with .meta as the extension

## Read Configuration Parameters from File

* We haven't done this at all.

    * The AT&T code for this is available to us through gitlab, they have a structure for doing this configuration, but it needs to be integrated with our code

## HLS

* Our method of sampling the throughput every chunk will not work with HLS

    * We rely on our alternative implementation of DashChunkSource to supply the chunks to the throughput sampler and sample processor

        * We need some way of identifying the chunks (whether chunk index or chunk media times)

            * Used for checking the details on future chunks

            * Used for ignoring duplicate calls to `updateSelectedTrack()`

    * It's not possible to do the same with HLS – HlsChunkSource is a final package-private class, so we can't subtype it

    * This also affects time-based sampling, as we are using the delivery of a chunk as a cue that we should deliver a premature time-based sample

* We may have to modify the ExoPlayer library to make this work

## Variable Chunk Duration

We assume a constant chunk duration in the stream in a couple of places in our calculations:

* Finding out how many segments are left in the video

    * This could probably be replaced with finding out how much time is left in the video

* Checking the actual rate of future chunks

    * This can be fixed by including the chunk durations in the metadata – at the moment I think we just have the sizes

* Calculating chunk deadlines in OscarH

    * This can be fixed by specifying the reservoir size in time units

* Filling the `maxSegSize` array in OscarH

    * I don't know what this does, so I can't say how to fix it

The algorithms will probably also need to be checked to make sure they still make sense with variable chunk durations – as far as I can see they mostly do.

## Integrated with AT&T Throughput Guidance

* AT&T implemented the throughput guidance by implementing the `BandwidthMeter` interface, so providing it as the result of calling `getBitrateEstimate()` on the guided bandwidth meter.

    * Our algorithms require a number of different values from the sample processors, though.

* At the moment our algorithms are not cleanly split into bandwidth estimation and track selection – if they were, we would have a much easier time of things working with ExoPlayer

    * This would require there being a single value interaction between the two though – just `getBitrateEstimate()`

    * Although, I suppose we could extend `BandwidthMeter` for some cases?

    * We have a `SampleProcessor` interface with a number of different functions that can be called – a better implementation might be different `BandwidthMeter` classes for each algorithm (where needed), that can reuse the functions currently defined in `SampleProcessor`

    * It might be difficult to make this separation cleanly – for example in the case of Arbiter+

* Are the algorithms going to trust the throughput guidance value completely? In that case, are the algorithms really themselves anymore?
