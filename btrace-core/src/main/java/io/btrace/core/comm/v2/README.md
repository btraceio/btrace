# BTrace Binary Protocol (v2)

This package contains a new binary protocol implementation for BTrace that replaces the original Java serialization-based protocol with a more efficient binary format.

## Benefits

- **Improved Performance**: Custom binary serialization is significantly faster than Java serialization
- **Reduced Memory Usage**: Binary format is more compact than Java serialization
- **Compression Support**: Message text larger than 1 KB is automatically compressed
- **Thread Safety**: Uses ReentrantLock instead of synchronized blocks for better scalability
- **Versioning**: Protocol includes version information for future compatibility
- **Extensibility**: Registry pattern makes it easy to add new command types
- **Backward Compatibility**: Adapter layer allows gradual migration

## Protocol Format

Each command in the binary protocol has the following format:

```
+----------------+----------------+--------------------+
| Protocol Version (1 byte) | Command Type (1 byte) | Command Data |
+----------------+----------------+--------------------+
```

The protocol version byte is currently `3` (`BinaryProtocol.VERSION`).

The Command Data format depends on the Command Type and is defined by each command implementation.

## Command Types

The binary protocol supports all the command types from the original protocol, plus `HEARTBEAT`:

| Type | Command | Description |
|------|---------|-------------|
| 0 | ERROR | Error notification |
| 1 | EVENT | Event trigger |
| 2 | EXIT | Exit command |
| 3 | INSTRUMENT | Instrumentation code |
| 4 | MESSAGE | Text message |
| 5 | RENAME | Rename command |
| 6 | STATUS | Status information |
| 7 | NUMBER_MAP | Number map data |
| 8 | STRING_MAP | String map data |
| 9 | NUMBER | Number data |
| 10 | GRID_DATA | Grid/tabular data |
| 11 | RETRANSFORMATION_START | Class retransformation start |
| 12 | RETRANSFORM_CLASS | Class retransformation notification |
| 13 | SET_PARAMS | Settings parameters |
| 14 | LIST_PROBES | List probes command |
| 15 | DISCONNECT | Disconnect command |
| 16 | RECONNECT | Reconnect command |
| 17 | LIST_FAILED_EXTENSIONS | List failed extensions command |
| 18 | HEARTBEAT | Connection liveness check (V2 only) |

## Data Types

The binary protocol supports the following data types:

- byte, int, long, float, double
- boolean
- String (UTF-8 encoded with length prefix)
- byte[] (with length prefix)
- Nested data structures (maps, lists, etc.)

## Compression

Message text larger than 1024 bytes (UTF-8) is automatically compressed using Java's Deflater/Inflater with BEST_SPEED setting. The threshold is a fixed constant in `BinaryMessageCommand`; other payloads, including instrumentation code, are not compressed.

## Migration

Use the `CommandAdapter` class to convert between binary commands and original commands:

```java
// Convert original command to binary command
BinaryCommand binaryCmd = CommandAdapter.toBinaryCommand(originalCmd);

// Convert binary command to original command
Command originalCmd = CommandAdapter.toBtraceCommand(binaryCmd);
```

For applications using the core BTrace API directly, use `BinaryClient` instead of the original client:

```java
// Create a binary client
BinaryClient client = new BinaryClient(inputStream, outputStream, commandListener);

// Send a command
client.sendMessage("Hello, World!", true);

// Process commands
client.commandLoop();
```

## Performance Comparison

The design target is 3-6x faster serialization than the original Java serialization-based protocol; measure it with the JMH benchmarks (`btrace-core/JMH_BENCHMARKS.md`). Wire size is about the same as the original protocol, except for compressed messages:

| Command | Original size | Binary size |
|---------|---------------|-------------|
| InstrumentCommand (100 KB of incompressible bytes) | 102,915 bytes | 102,410 bytes |
| MessageCommand (100 characters) | 120 bytes | 116 bytes |
| MessageCommand (10 KB of repetitive text) | 10,336 bytes | 175 bytes (compressed) |

## Implementation

The implementation follows a clean, object-oriented design:

- `BinaryProtocol`: Low-level binary serialization utilities
- `BinaryCommand`: Base class for all commands
- `BinaryWireIO`: Wire protocol implementation
- `BinaryClient`: Client wrapper for the binary protocol
- Command implementations: One class per command type
- `CommandAdapter`: Conversion between binary and original commands

## Future Enhancements

- Add more compression algorithms (LZ4, Snappy)
- Implement batching for multiple commands
- Add flow control and backpressure handling
- Add direct ByteBuffer support for zero-copy operations
- Implement multiplexing for multiple concurrent clients
