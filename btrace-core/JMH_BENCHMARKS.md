# JMH Benchmarks for Binary Protocol v2

## Overview

This document describes how to run the JMH (Java Microbenchmark Harness) benchmarks for the binary protocol v2 implementation.

The benchmarks compare the performance of the new binary protocol against Java serialization across multiple command types and data sizes.

## Running Benchmarks

### Run All Benchmarks

```bash
./gradlew :btrace-core:jmh
```

This will run all benchmarks with default settings:
- 3 warmup iterations
- 5 measurement iterations
- 2 forks
- 1 thread
- Both throughput and average time modes

### Filter Benchmarks

Use the `jmhInclude` property to filter which benchmarks to run:

```bash
# Run only serialization benchmarks
./gradlew :btrace-core:jmh -PjmhInclude=".*Serialize"

# Run only deserialization benchmarks
./gradlew :btrace-core:jmh -PjmhInclude=".*Deserialize"

# Run only round-trip benchmarks
./gradlew :btrace-core:jmh -PjmhInclude=".*RoundTrip"

```

`jmhInclude` matches benchmark method names. The command type and the data size are JMH
parameters (`commandType`, `dataSize`), not part of the name; select them with `-p`:

```bash
# Run benchmarks for specific command type
./gradlew :btrace-core:jmh --jmhArgs="-p commandType=InstrumentCommand"

# Run benchmarks for specific data size
./gradlew :btrace-core:jmh --jmhArgs="-p dataSize=small"
```

## Benchmark Structure

### Command Types Tested

The benchmarks test the following command types:
1. **InstrumentCommand** - Bytecode instrumentation with byte arrays
2. **MessageCommand** - Text messages
3. **ErrorCommand** - Error reporting
4. **EventCommand** - Event notifications
5. **StatusCommand** - Status updates
6. **NumberMapDataCommand** - Maps with numeric values
7. **StringMapDataCommand** - Maps with string values
8. **NumberDataCommand** - Single numeric values
9. **GridDataCommand** - Tabular data
10. **ExitCommand** - Exit signals

### Data Sizes

Each command type is tested with three data sizes:
- **small** - Minimal data (e.g., 50 chars, 100 bytes, 5 entries)
- **medium** - Moderate data (e.g., 500 chars, 1KB, 50 entries)
- **large** - Large data (e.g., 5000 chars, 10KB, 500 entries)

### Benchmark Methods

Each command type/size combination is tested with:
- `binarySerialize` - Serialize using binary protocol
- `binaryDeserialize` - Deserialize using binary protocol
- `javaSerialize` - Serialize using Java serialization
- `javaDeserialize` - Deserialize using Java serialization
- `binaryRoundTrip` - Full round-trip with binary protocol
- `javaRoundTrip` - Full round-trip with Java serialization

## Interpreting Results

### Throughput (ops/ms)
Higher is better. Shows how many operations per millisecond (`timeUnit = 'ms'` in `btrace-core/build.gradle` overrides the benchmark's own microsecond unit).

### Average Time (ms/op)
Lower is better. Shows milliseconds per operation.

### Example Output

```
Benchmark                                    (commandType)  (dataSize)  Mode  Cnt   Score   Error   Units
BinaryProtocolBenchmark.binarySerialize     MessageCommand       small  avgt   10   0.005 ± 0.001   ms/op
BinaryProtocolBenchmark.javaSerialize       MessageCommand       small  avgt   10   0.012 ± 0.001   ms/op
```

In this illustrative output (not a recorded run) binary serialization would be ~2.4x faster than Java serialization for small messages.

## Expected Performance Improvements

Based on architecture design goals, the binary protocol should deliver:

| Metric | Target Improvement |
|--------|-------------------|
| Serialization Speed | 3-6x faster |
| Deserialization Speed | 3-6x faster |
| Wire Size | About the same, except compressed messages |
| Memory Allocation | Significantly reduced |

With compression enabled (for messages >1KB):
- Wire size improvements can be 10-100x for highly compressible data
- Speed may be slower due to compression overhead

## Customizing Benchmarks

Edit `btrace-core/build.gradle` to change JMH settings:

```gradle
jmh {
    warmupIterations = 3      // Number of warmup iterations (-Pjmh.warmupIterations=<n>)
    iterations = 5            // Number of measurement iterations (-Pjmh.iterations=<n>)
    fork = 2                  // Number of forked JVM processes (-Pjmh.fork=<n>)
    threads = 1               // Number of worker threads
    timeUnit = 'ms'          // Time unit for results
    includes = ['.*BinaryProtocol.*'] // Benchmarks to run (overridden by -PjmhInclude)
    benchmarkMode = ['avgt', 'thrpt']  // Benchmark modes
}
```

## Benchmark Output Location

JMH results are written to:
- Console output during execution
- `btrace-core/build/results/jmh/results.txt` (the JMH Gradle plugin's default results file)

## Best Practices

1. **Close other applications** - Minimize system noise
2. **Run multiple times** - JMH forks handle this automatically
3. **Check for GC** - Look for GC pauses in output
4. **Compare same system** - Run before/after on same hardware
5. **Use realistic data** - Adjust data sizes to match production

## Advanced Usage

### Custom JMH Options

Pass JMH options directly:

```bash
./gradlew :btrace-core:jmh -PjmhInclude=".*Serialize" \
  --jmhArgs="-prof gc -prof stack -rf json -rff results.json"
```

Common profilers:
- `-prof gc` - GC profiling
- `-prof stack` - Stack traces
- `-prof perfnorm` - Normalized perf events

### Output Formats

- `-rf csv` - CSV output
- `-rf json` - JSON output
- `-rf text` - Text output (default)

## Troubleshooting

### Out of Memory
Increase the heap of the forked benchmark JVMs:
```bash
./gradlew :btrace-core:jmh --jmhArgs="-jvmArgs -Xmx4g"
```

### Benchmarks Take Too Long
Reduce iterations:
```bash
./gradlew :btrace-core:jmh -Pjmh.warmupIterations=1 -Pjmh.iterations=2 -Pjmh.fork=1
```

### Need More Detail
Enable JMH verbose output:
```bash
./gradlew :btrace-core:jmh --jmhArgs="-v EXTRA"
```
