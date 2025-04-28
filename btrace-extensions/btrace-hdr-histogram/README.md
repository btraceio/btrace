# BTrace HDR Histogram Extension

This extension provides integration with the [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram) library for BTrace. HdrHistogram is a high dynamic range histogram implementation that supports recording and analyzing sampled data value counts across a configurable integer value range with configurable value precision.

## Features

- High dynamic range: The histogram can track values from 1 to 3,600,000,000,000 (by default) with a configurable precision.
- Low latency: The histogram is designed to be used in latency-sensitive applications.
- Configurable precision: The number of significant digits can be configured to trade off precision for memory usage.
- Percentile calculations: The histogram can calculate percentiles with high accuracy.

## Usage

### Histograms

The `Histograms` class provides utility methods for creating and managing HDR Histograms.

```java
// Create a histogram with default settings
Histograms.newHistogram("my-histogram");

// Create a histogram with custom settings
Histograms.newHistogram("my-custom-histogram", 1000000, 2);

// Record a value in a histogram
Histograms.recordValue("my-histogram", 42);

// Print histogram statistics
Histograms.printHistogram("my-histogram");

// Reset a histogram
Histograms.resetHistogram("my-histogram");
```

### Recorders

The `Recorders` class provides utility methods for creating and managing HDR Histogram recorders. Recorders are useful for recording values over time and then analyzing them later.

```java
// Create a recorder with default settings
Recorders.newRecorder("my-recorder");

// Create a recorder with custom settings
Recorders.newRecorder("my-custom-recorder", 1000000, 2);

// Record a value in a recorder
Recorders.recordValue("my-recorder", 42);

// Get a histogram from a recorder
Histogram histogram = Recorders.getHistogram("my-recorder");

// Get a histogram from a recorder and reset the recorder
Histogram histogram = Recorders.getHistogramAndReset("my-recorder");

// Print recorder histogram statistics
Recorders.printHistogram("my-recorder");

// Reset a recorder
Recorders.resetRecorder("my-recorder");
```

## Sample

See the [HdrHistogramSample.java](src/main/java/org/openjdk/btrace/extensions/hdrhistogram/samples/HdrHistogramSample.java) file for a complete example of how to use the HDR Histogram extension in a BTrace script.

## License

This extension is licensed under the GPL v2 with Classpath exception, the same license as BTrace.