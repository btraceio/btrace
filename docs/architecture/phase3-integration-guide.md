# Phase 3 Integration Guide: WireProtocol Runtime Integration

## Overview

This document provides guidance for integrating the WireProtocol abstraction into BTrace's RemoteClient (agent-side) and Client (client-side) classes.

## Current State (Phase 3.1 Complete)

✅ **Completed:**
- ProtocolVersion enum with V1/V2 definitions
- ProtocolNegotiator for automatic protocol detection
- ProtocolConfig for configuration management
- WireProtocol interface with factory methods
- JavaSerializationProtocol (V1 implementation)
- BinaryWireProtocol (V2 implementation)
- Comprehensive test coverage (113 tests passing)

🔄 **Ready for Integration:**
- btrace-agent RemoteClient class
- btrace-client Client class

## Integration Strategy

### Phase 3.3: RemoteClient Integration (Agent-Side)

**Location:** `btrace-agent/src/main/java/org/openjdk/btrace/agent/RemoteClient.java`

**Current Implementation:**
```java
private volatile ObjectInputStream ois;
private volatile ObjectOutputStream oos;

// In getClient():
ObjectInputStream ois = new ObjectInputStream(sock.getInputStream());
ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
Command cmd = WireIO.read(ois);
WireIO.write(oos, cmd);
```

**Proposed Changes:**
```java
private volatile WireProtocol protocol;

// In getClient():
InputStream is = sock.getInputStream();
OutputStream os = sock.getOutputStream();

// Option 1: Auto-negotiation (recommended)
WireProtocol protocol = WireProtocol.createWithNegotiation(is, os);

// Option 2: Configuration-based
ProtocolConfig config = ProtocolConfig.fromSystemProperties();
WireProtocol protocol = WireProtocol.createWithConfig(config, is, os);

// Read/write commands
Command cmd = protocol.read();
protocol.write(cmd);
```

**Key Considerations:**
1. **Backward Compatibility:** Negotiation ensures V1 clients still work
2. **Thread Safety:** WireProtocol implementations handle synchronization
3. **Stream Access:** For code that needs direct stream access, JavaSerializationProtocol provides getters
4. **Atomic Updates:** Use existing AtomicReferenceFieldUpdater pattern
5. **Reconnection:** Protocol must be re-negotiated on reconnect

**Implementation Steps:**
1. Add `private volatile WireProtocol protocol` field
2. Update `getClient()` to use `WireProtocol.createWithNegotiation()`
3. Replace `WireIO.read(ois)` with `protocol.read()`
4. Replace `WireIO.write(oos, cmd)` with `protocol.write(cmd)`
5. Update `initClient()` command loop
6. Update `reconnect()` to re-create protocol
7. Update `closeAll()` to call `protocol.close()`
8. Add AtomicReferenceFieldUpdater for protocol field

### Phase 3.4: Client Integration (Client-Side)

**Location:** `btrace-client/src/main/java/org/openjdk/btrace/client/Client.java`

**Current Implementation:**
```java
private volatile ObjectInputStream ois;
private volatile ObjectOutputStream oos;

// In submit():
oos = new ObjectOutputStream(sock.getOutputStream());
WireIO.write(oos, new SetSettingsCommand(settings));
ois = new ObjectInputStream(sock.getInputStream());
oos.reset();
WireIO.write(oos, new InstrumentCommand(code, args));
Command cmd = WireIO.read(ois);
```

**Proposed Changes:**
```java
private volatile WireProtocol protocol;

// In submit():
InputStream is = sock.getInputStream();
OutputStream os = sock.getOutputStream();

// Client initiates with preferred protocol (V2)
ProtocolConfig config = ProtocolConfig.builder()
    .version(ProtocolVersion.V2)
    .autoNegotiate(true)
    .build();

// Send magic bytes for V2, or fall back to V1 on timeout
try {
    if (config.getVersion() == ProtocolVersion.V2) {
        os.write(ProtocolVersion.V2.getMagicBytes());
        os.flush();
    }
    protocol = WireProtocol.create(config.getVersion(), is, os);
} catch (IOException e) {
    // Fallback to V1 on timeout (old agent)
    protocol = WireProtocol.create(ProtocolVersion.V1, is, os);
}

// Read/write commands
protocol.write(new SetSettingsCommand(settings));
protocol.write(new InstrumentCommand(code, args));
Command cmd = protocol.read();
```

**Key Considerations:**
1. **V2 First:** Client should attempt V2 with magic bytes
2. **Fallback Mechanism:** Timeout (5s) indicates V1-only agent
3. **No Reset Needed:** Binary protocol doesn't require reset()
4. **Stream Ordering:** Magic bytes sent before protocol creation
5. **Connection Retry:** Protocol re-created on each connection attempt

**Implementation Steps:**
1. Add `private volatile WireProtocol protocol` field
2. Update `connectAndListProbes()` to use protocol negotiation
3. Update `submit()` to send V2 magic bytes with timeout fallback
4. Replace `WireIO.write(oos, cmd)` with `protocol.write(cmd)`
5. Replace `WireIO.read(ois)` with `protocol.read()`
6. Remove `oos.reset()` calls (not needed for V2)
7. Update `commandLoop()` to use protocol
8. Update `close()` to call `protocol.close()`

## Configuration Options

### System Properties

Users can control protocol behavior:

```bash
# Force V1 (for debugging or compatibility)
-Dbtrace.comm.protocol=v1 -Dbtrace.comm.forceVersion=true

# Force V2 (fail if agent doesn't support)
-Dbtrace.comm.protocol=v2 -Dbtrace.comm.forceVersion=true

# Auto-negotiate (default, recommended)
-Dbtrace.comm.autoNegotiate=true
```

### Environment Variables

For containerized environments:

```bash
export BTRACE_COMM_PROTOCOL=v2
export BTRACE_COMM_AUTO_NEGOTIATE=true
```

## Testing Strategy

### Unit Tests

**RemoteClient Tests:**
- Protocol negotiation with V1 client
- Protocol negotiation with V2 client
- Reconnection with protocol re-negotiation
- Command read/write through protocol
- Error handling and fallback

**Client Tests:**
- V2 connection to V2 agent (happy path)
- V1 connection to V1 agent (legacy)
- V2 client with V1 agent (fallback)
- Connection timeout and retry
- Protocol version mismatch handling

### Integration Tests

**End-to-End Scenarios:**
1. V2 client → V2 agent (modern setup)
2. V1 client → V2 agent (legacy client)
3. V2 client → V1 agent (legacy agent with fallback)
4. V1 client → V1 agent (full legacy)
5. Multiple clients with mixed protocols
6. Reconnection scenarios
7. Large message handling with compression
8. Performance benchmarking

### Compatibility Matrix

| Client Version | Agent Version | Expected Behavior |
|---------------|---------------|-------------------|
| V2 | V2 | V2 protocol, optimal performance |
| V2 | V1 | Fallback to V1 after timeout |
| V1 | V2 | V1 protocol (agent detects) |
| V1 | V1 | V1 protocol (legacy) |

## Migration Path

### Phase 1: Add WireProtocol Support (Non-Breaking)
- Add WireProtocol field alongside existing streams
- Implement protocol creation logic
- Keep existing code paths functional
- Add feature flag for gradual rollout

### Phase 2: Use WireProtocol Internally (Testing)
- Route all I/O through WireProtocol
- Maintain stream accessors for compatibility
- Run extensive integration tests
- Monitor for regressions

### Phase 3: Deprecate Direct Stream Access
- Mark direct stream usage as deprecated
- Document migration guide for extensions
- Provide compatibility period (2-3 releases)

### Phase 4: Remove Legacy Code (Future)
- Remove deprecated stream accessors
- Simplify codebase
- Full commitment to WireProtocol abstraction

## Performance Considerations

### V2 Protocol Benefits

**Serialization Performance:**
- 3-6x faster than ObjectOutputStream
- Reduced GC pressure (fewer allocations)
- Better CPU cache utilization

**Wire Size:**
- 2-5x smaller base payload
- 10-100x with compression (messages >1KB)
- Reduced network bandwidth
- Faster transmission

**Scalability:**
- ReentrantLock vs synchronized
- Better thread contention handling
- Supports higher connection counts

### Monitoring

Add metrics for protocol usage:
```java
// Track protocol version distribution
meterRegistry.counter("btrace.protocol.version", "version", "v1").increment();
meterRegistry.counter("btrace.protocol.version", "version", "v2").increment();

// Track negotiation failures
meterRegistry.counter("btrace.protocol.negotiation.failures").increment();

// Track message sizes
meterRegistry.summary("btrace.protocol.message.size").record(size);
```

## Rollout Strategy

### Stage 1: Canary (1% of agents)
- Enable V2 on small subset
- Monitor error rates
- Validate performance improvements
- Quick rollback if issues

### Stage 2: Beta (10% of agents)
- Expand to larger subset
- Gather performance data
- Test in diverse environments
- Validate compression benefits

### Stage 3: GA (100% of agents)
- Full rollout
- V2 becomes default
- V1 remains supported
- Document best practices

## Troubleshooting

### Issue: Connection Timeout
**Symptom:** Client hangs for 5 seconds before connecting
**Cause:** V2 client trying to connect to V1-only agent
**Solution:** Use `-Dbtrace.comm.protocol=v1` on client

### Issue: Protocol Version Mismatch
**Symptom:** Connection rejected with protocol error
**Cause:** forceVersion=true with mismatched agent
**Solution:** Enable auto-negotiation or match versions

### Issue: Degraded Performance
**Symptom:** V2 slower than expected
**Cause:** Compression overhead on small messages
**Solution:** Adjust compression threshold (default 1KB)

### Issue: Reconnection Failure
**Symptom:** Reconnect doesn't work with V2
**Cause:** Protocol not re-negotiated on reconnect
**Solution:** Ensure protocol re-creation in reconnect()

## References

- [V2 Protocol Architecture](./v2-protocol-architecture.md)
- [JMH Benchmarks Guide](../btrace-core/JMH_BENCHMARKS.md)
- BTrace Wiki: Protocol Negotiation
- BTrace Wiki: Performance Tuning

## Summary

The WireProtocol abstraction provides a clean migration path from V1 to V2 while maintaining full backward compatibility. The integration into RemoteClient and Client is straightforward and can be done incrementally with proper testing at each stage.

**Key Success Factors:**
1. ✅ Automatic protocol negotiation
2. ✅ Transparent fallback to V1
3. ✅ No breaking changes
4. ✅ Comprehensive test coverage
5. ✅ Performance monitoring
6. ✅ Clear migration path

**Next Steps:**
1. Implement RemoteClient integration
2. Implement Client integration
3. Add integration tests
4. Performance validation
5. Documentation updates
6. Gradual rollout
