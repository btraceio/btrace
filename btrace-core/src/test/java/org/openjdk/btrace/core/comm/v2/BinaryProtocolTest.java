package org.openjdk.btrace.core.comm.v2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.ArgsMap;

/**
 * Tests for the binary protocol implementation.
 */
public class BinaryProtocolTest {

    @Test
    public void testBinaryProtocolBasicTypes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Write test data
        BinaryProtocol.writeByte(baos, (byte) 123);
        BinaryProtocol.writeInt(baos, 42);
        BinaryProtocol.writeLong(baos, 9876543210L);
        BinaryProtocol.writeFloat(baos, 3.14f);
        BinaryProtocol.writeDouble(baos, 2.71828);
        BinaryProtocol.writeBoolean(baos, true);
        BinaryProtocol.writeString(baos, "Hello, World!");
        byte[] testArray = {1, 2, 3, 4, 5};
        BinaryProtocol.writeByteArray(baos, testArray);
        
        // Read test data
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        assertEquals((byte) 123, BinaryProtocol.readByte(bais));
        assertEquals(42, BinaryProtocol.readInt(bais));
        assertEquals(9876543210L, BinaryProtocol.readLong(bais));
        assertEquals(3.14f, BinaryProtocol.readFloat(bais));
        assertEquals(2.71828, BinaryProtocol.readDouble(bais));
        assertTrue(BinaryProtocol.readBoolean(bais));
        assertEquals("Hello, World!", BinaryProtocol.readString(bais));
        byte[] readArray = BinaryProtocol.readByteArray(bais);
        assertArrayEquals(testArray, readArray);
    }
    
    @Test
    public void testBinaryExitCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Create and write command
        BinaryExitCommand original = new BinaryExitCommand(42);
        BinaryWireIO.write(baos, original);
        
        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);
        
        // Verify command
        assertTrue(readCommand instanceof BinaryExitCommand);
        BinaryExitCommand exitCommand = (BinaryExitCommand) readCommand;
        assertEquals(42, exitCommand.getExitCode());
        assertEquals(BinaryCommand.EXIT, exitCommand.getType());
        assertTrue(exitCommand.isUrgent());
    }
    
    @Test
    public void testBinaryMessageCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Create and write command
        BinaryMessageCommand original = new BinaryMessageCommand("Test message", true);
        BinaryWireIO.write(baos, original);
        
        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);
        
        // Verify command
        assertTrue(readCommand instanceof BinaryMessageCommand);
        BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
        assertEquals("Test message", msgCommand.getMessage());
        assertEquals(BinaryCommand.MESSAGE, msgCommand.getType());
        assertEquals(original.isUrgent(), msgCommand.isUrgent());
    }
    
    @Test
    public void testBinaryMessageCommandWithCompression() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Create a large message that will trigger compression
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("This is a test message that will be compressed. ");
        }
        String largeMessage = sb.toString();
        
        // Create and write command
        BinaryMessageCommand original = new BinaryMessageCommand(largeMessage, true);
        BinaryWireIO.write(baos, original);
        
        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);
        
        // Verify command
        assertTrue(readCommand instanceof BinaryMessageCommand);
        BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
        assertEquals(largeMessage, msgCommand.getMessage());
    }
    
    @Test
    public void testBinaryInstrumentCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Create test data
        byte[] code = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        Map<String, String> args = new HashMap<>();
        args.put("key1", "value1");
        args.put("key2", "value2");
        
        // Create and write command
        BinaryInstrumentCommand original = new BinaryInstrumentCommand(code, args);
        BinaryWireIO.write(baos, original);
        
        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);
        
        // Verify command
        assertTrue(readCommand instanceof BinaryInstrumentCommand);
        BinaryInstrumentCommand instrCommand = (BinaryInstrumentCommand) readCommand;
        assertArrayEquals(code, instrCommand.getCode());
        ArgsMap readArgs = instrCommand.getArguments();
        assertEquals(2, readArgs.size());
        assertEquals("value1", readArgs.get("key1"));
        assertEquals("value2", readArgs.get("key2"));
    }
    
    @Test
    public void testBinaryGridDataCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Create test data
        List<String> columnNames = new ArrayList<>();
        columnNames.add("Column1");
        columnNames.add("Column2");
        columnNames.add("Column3");
        
        List<Object[]> data = new ArrayList<>();
        data.add(new Object[] { "Row1Col1", 123, 3.14 });
        data.add(new Object[] { "Row2Col1", 456, 2.71 });
        data.add(new Object[] { "Row3Col1", 789, "Row3Col3" });
        
        // Create and write command
        BinaryGridDataCommand original = new BinaryGridDataCommand("TestGrid", columnNames, data);
        BinaryWireIO.write(baos, original);
        
        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);
        
        // Verify command
        assertTrue(readCommand instanceof BinaryGridDataCommand);
        BinaryGridDataCommand gridCommand = (BinaryGridDataCommand) readCommand;
        assertEquals("TestGrid", gridCommand.getName());
        
        List<String> readColumnNames = gridCommand.getColumnNames();
        assertEquals(3, readColumnNames.size());
        assertEquals("Column1", readColumnNames.get(0));
        assertEquals("Column2", readColumnNames.get(1));
        assertEquals("Column3", readColumnNames.get(2));
        
        List<Object[]> readData = gridCommand.getData();
        assertEquals(3, readData.size());
        assertEquals("Row1Col1", readData.get(0)[0]);
        assertEquals(123, readData.get(0)[1]);
        assertEquals(3.14, readData.get(0)[2]);
        assertEquals("Row2Col1", readData.get(1)[0]);
        assertEquals(456, readData.get(1)[1]);
        assertEquals(2.71, readData.get(1)[2]);
        assertEquals("Row3Col1", readData.get(2)[0]);
        assertEquals(789, readData.get(2)[1]);
        assertEquals("Row3Col3", readData.get(2)[2]);
    }
    
    @Test
    public void testCommandAdapter() throws IOException {
        // Test command conversion from binary to original and back
        BinaryExitCommand exitCmd = new BinaryExitCommand(42);
        org.openjdk.btrace.core.comm.Command originalCmd = CommandAdapter.toBtraceCommand(exitCmd);
        assertEquals(org.openjdk.btrace.core.comm.Command.EXIT, originalCmd.getType());

        BinaryCommand convertedBack = CommandAdapter.toBinaryCommand(originalCmd);
        assertTrue(convertedBack instanceof BinaryExitCommand);
        assertEquals(42, ((BinaryExitCommand) convertedBack).getExitCode());
    }

    // Tests for the 13 previously untested command types

    @Test
    public void testBinaryErrorCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command
        BinaryErrorCommand original =
            new BinaryErrorCommand(
                "java.lang.IllegalStateException", "Test error message", "stack-trace");
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryErrorCommand);
        BinaryErrorCommand errorCommand = (BinaryErrorCommand) readCommand;
        assertEquals("java.lang.IllegalStateException", errorCommand.getExceptionClass());
        assertEquals("Test error message", errorCommand.getMessage());
        assertEquals("stack-trace", errorCommand.getStackTrace());
        assertEquals(BinaryCommand.ERROR, errorCommand.getType());
    }

    @Test
    public void testBinaryErrorCommandNullMessage() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command with null message
        BinaryErrorCommand original = new BinaryErrorCommand("java.lang.RuntimeException", null, null);
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryErrorCommand);
        BinaryErrorCommand errorCommand = (BinaryErrorCommand) readCommand;
        assertEquals("java.lang.RuntimeException", errorCommand.getExceptionClass());
        assertEquals(null, errorCommand.getMessage());
        assertEquals(null, errorCommand.getStackTrace());
    }

    @Test
    public void testBinaryEventCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command
        BinaryEventCommand original = new BinaryEventCommand("test.event.fired");
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryEventCommand);
        BinaryEventCommand eventCommand = (BinaryEventCommand) readCommand;
        assertEquals("test.event.fired", eventCommand.getEvent());
        assertEquals(BinaryCommand.EVENT, eventCommand.getType());
    }

    @Test
    public void testBinaryRenameCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command
        BinaryRenameCommand original = new BinaryRenameCommand("NewProbeName");
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryRenameCommand);
        BinaryRenameCommand renameCommand = (BinaryRenameCommand) readCommand;
        assertEquals("NewProbeName", renameCommand.getNewName());
        assertEquals(BinaryCommand.RENAME, renameCommand.getType());
    }

    @Test
    public void testBinaryStatusCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Test success status
        BinaryStatusCommand original = new BinaryStatusCommand(1, true);
        BinaryWireIO.write(baos, original);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        assertTrue(readCommand instanceof BinaryStatusCommand);
        BinaryStatusCommand statusCommand = (BinaryStatusCommand) readCommand;
        assertEquals(1, statusCommand.getFlag());
        assertTrue(statusCommand.isSuccess());
        assertEquals(BinaryCommand.STATUS, statusCommand.getType());
    }

    @Test
    public void testBinaryStatusCommandFailure() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Test failure status
        BinaryStatusCommand original = new BinaryStatusCommand(2, false);
        BinaryWireIO.write(baos, original);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        assertTrue(readCommand instanceof BinaryStatusCommand);
        BinaryStatusCommand statusCommand = (BinaryStatusCommand) readCommand;
        assertEquals(2, statusCommand.getFlag());
        assertTrue(!statusCommand.isSuccess());
    }

    @Test
    public void testBinaryNumberMapDataCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create test data with various number types
        Map<String, Number> data = new HashMap<>();
        data.put("intValue", 42);
        data.put("longValue", 9876543210L);
        data.put("floatValue", 3.14f);
        data.put("doubleValue", 2.71828);
        data.put("bigIntValue", new BigInteger("123456789012345678901234567890"));
        data.put("bigDecValue", new BigDecimal("12345.67890123456789"));

        // Create and write command
        BinaryNumberMapDataCommand original = new BinaryNumberMapDataCommand("TestNumberMap", data);
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryNumberMapDataCommand);
        BinaryNumberMapDataCommand mapCommand = (BinaryNumberMapDataCommand) readCommand;
        assertEquals("TestNumberMap", mapCommand.getName());
        assertEquals(BinaryCommand.NUMBER_MAP, mapCommand.getType());

        Map<String, Number> readData = mapCommand.getData();
        assertEquals(6, readData.size());
        assertEquals(42, readData.get("intValue").intValue());
        assertEquals(9876543210L, readData.get("longValue").longValue());
        assertEquals(3.14f, readData.get("floatValue").floatValue(), 0.001);
        assertEquals(2.71828, readData.get("doubleValue").doubleValue(), 0.00001);
        assertEquals(
            new BigInteger("123456789012345678901234567890"), readData.get("bigIntValue"));
        assertEquals(new BigDecimal("12345.67890123456789"), readData.get("bigDecValue"));
    }

    @Test
    public void testBinaryNumberMapDataCommandEmpty() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command with empty map
        Map<String, Number> data = new HashMap<>();
        BinaryNumberMapDataCommand original = new BinaryNumberMapDataCommand("EmptyMap", data);
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryNumberMapDataCommand);
        BinaryNumberMapDataCommand mapCommand = (BinaryNumberMapDataCommand) readCommand;
        assertEquals("EmptyMap", mapCommand.getName());
        assertTrue(mapCommand.getData().isEmpty());
    }

    @Test
    public void testBinaryStringMapDataCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create test data
        Map<String, String> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");
        data.put("key3", "value3");

        // Create and write command
        BinaryStringMapDataCommand original = new BinaryStringMapDataCommand("TestStringMap", data);
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryStringMapDataCommand);
        BinaryStringMapDataCommand mapCommand = (BinaryStringMapDataCommand) readCommand;
        assertEquals("TestStringMap", mapCommand.getName());
        assertEquals(BinaryCommand.STRING_MAP, mapCommand.getType());

        Map<String, String> readData = mapCommand.getData();
        assertEquals(3, readData.size());
        assertEquals("value1", readData.get("key1"));
        assertEquals("value2", readData.get("key2"));
        assertEquals("value3", readData.get("key3"));
    }

    @Test
    public void testBinaryNumberDataCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Test with Integer
        BinaryNumberDataCommand original = new BinaryNumberDataCommand("TestInt", 42);
        BinaryWireIO.write(baos, original);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        assertTrue(readCommand instanceof BinaryNumberDataCommand);
        BinaryNumberDataCommand numberCommand = (BinaryNumberDataCommand) readCommand;
        assertEquals("TestInt", numberCommand.getName());
        assertEquals(42, numberCommand.getValue().intValue());
        assertEquals(BinaryCommand.NUMBER, numberCommand.getType());
    }

    @Test
    public void testBinaryNumberDataCommandDouble() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Test with Double
        BinaryNumberDataCommand original = new BinaryNumberDataCommand("TestDouble", 3.14159);
        BinaryWireIO.write(baos, original);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        assertTrue(readCommand instanceof BinaryNumberDataCommand);
        BinaryNumberDataCommand numberCommand = (BinaryNumberDataCommand) readCommand;
        assertEquals("TestDouble", numberCommand.getName());
        assertEquals(3.14159, numberCommand.getValue().doubleValue(), 0.00001);
    }

    @Test
    public void testBinaryNumberDataCommandLong() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Test with Long
        BinaryNumberDataCommand original = new BinaryNumberDataCommand("TestLong", 9876543210L);
        BinaryWireIO.write(baos, original);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        assertTrue(readCommand instanceof BinaryNumberDataCommand);
        BinaryNumberDataCommand numberCommand = (BinaryNumberDataCommand) readCommand;
        assertEquals("TestLong", numberCommand.getName());
        assertEquals(9876543210L, numberCommand.getValue().longValue());
    }

    @Test
    public void testBinaryNumberDataCommandBigDecimal() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Test with BigDecimal
        BinaryNumberDataCommand original =
            new BinaryNumberDataCommand("TestBigDecimal", new BigDecimal("12345.67890123456789"));
        BinaryWireIO.write(baos, original);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        assertTrue(readCommand instanceof BinaryNumberDataCommand);
        BinaryNumberDataCommand numberCommand = (BinaryNumberDataCommand) readCommand;
        assertEquals("TestBigDecimal", numberCommand.getName());
        assertEquals(new BigDecimal("12345.67890123456789"), numberCommand.getValue());
    }

    @Test
    public void testBinaryRetransformationStartNotification() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command
        BinaryRetransformationStartNotification original = new BinaryRetransformationStartNotification(150);
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryRetransformationStartNotification);
        BinaryRetransformationStartNotification notification = (BinaryRetransformationStartNotification) readCommand;
        assertEquals(150, notification.getNumClasses());
        assertEquals(BinaryCommand.RETRANSFORMATION_START, notification.getType());
    }

    @Test
    public void testBinaryRetransformClassNotification() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command
        BinaryRetransformClassNotification original = new BinaryRetransformClassNotification(
            "com.example.MyClass", 10, 150
        );
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryRetransformClassNotification);
        BinaryRetransformClassNotification notification = (BinaryRetransformClassNotification) readCommand;
        assertEquals("com.example.MyClass", notification.getClassName());
        assertEquals(10, notification.getIndex());
        assertEquals(150, notification.getTotal());
        assertEquals(BinaryCommand.RETRANSFORM_CLASS, notification.getType());
    }

    @Test
    public void testBinarySetSettingsCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create test settings
        Map<String, Object> settings = new HashMap<>();
        settings.put("debug", true);
        settings.put("timeout", 5000);
        settings.put("threshold", 1.5);
        settings.put("name", "TestProbe");

        // Create and write command
        BinarySetSettingsCommand original = new BinarySetSettingsCommand(settings);
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinarySetSettingsCommand);
        BinarySetSettingsCommand settingsCommand = (BinarySetSettingsCommand) readCommand;
        assertEquals(BinaryCommand.SET_PARAMS, settingsCommand.getType());

        Map<String, Object> readSettings = settingsCommand.getParams();
        assertEquals(4, readSettings.size());
        assertEquals(true, readSettings.get("debug"));
        assertEquals(5000, readSettings.get("timeout"));
        assertEquals(1.5, ((Number) readSettings.get("threshold")).doubleValue(), 0.001);
        assertEquals("TestProbe", readSettings.get("name"));
    }

    @Test
    public void testBinaryListProbesCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create test probe list
        List<String> probes = new ArrayList<>();
        probes.add("probe1");
        probes.add("probe2");
        probes.add("probe3");

        // Create and write command
        BinaryListProbesCommand original = new BinaryListProbesCommand();
        original.setProbes(probes);
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryListProbesCommand);
        BinaryListProbesCommand listCommand = (BinaryListProbesCommand) readCommand;
        assertEquals(BinaryCommand.LIST_PROBES, listCommand.getType());

        List<String> readProbes = listCommand.getProbes();
        assertEquals(3, readProbes.size());
        assertEquals("probe1", readProbes.get(0));
        assertEquals("probe2", readProbes.get(1));
        assertEquals("probe3", readProbes.get(2));
    }

    @Test
    public void testBinaryListProbesCommandEmpty() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command with empty list
        BinaryListProbesCommand original = new BinaryListProbesCommand();
        original.setProbes(new ArrayList<>());
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryListProbesCommand);
        BinaryListProbesCommand listCommand = (BinaryListProbesCommand) readCommand;
        assertTrue(listCommand.getProbes().isEmpty());
    }

    @Test
    public void testBinaryDisconnectCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command
        BinaryDisconnectCommand original = new BinaryDisconnectCommand("probe-12345");
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryDisconnectCommand);
        BinaryDisconnectCommand disconnectCommand = (BinaryDisconnectCommand) readCommand;
        assertEquals("probe-12345", disconnectCommand.getProbeId());
        assertEquals(BinaryCommand.DISCONNECT, disconnectCommand.getType());
    }

    @Test
    public void testBinaryReconnectCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create and write command
        BinaryReconnectCommand original = new BinaryReconnectCommand("probe-67890");
        BinaryWireIO.write(baos, original);

        // Read command
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BinaryCommand readCommand = BinaryWireIO.read(bais);

        // Verify command
        assertTrue(readCommand instanceof BinaryReconnectCommand);
        BinaryReconnectCommand reconnectCommand = (BinaryReconnectCommand) readCommand;
        assertEquals("probe-67890", reconnectCommand.getProbeId());
        assertEquals(BinaryCommand.RECONNECT, reconnectCommand.getType());
    }
} 
