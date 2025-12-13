package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Binary implementation of the GridDataCommand.
 * This command is used to send tabular data from the BTrace agent to the client.
 */
public class BinaryGridDataCommand extends BinaryDataCommand {
    private List<String> columnNames = new ArrayList<>();
    private List<Object[]> data = new ArrayList<>();

    static {
        // Register this command type
        BinaryCommand.registerCommand(GRID_DATA, BinaryGridDataCommand::new);
    }

    public BinaryGridDataCommand(String name, List<String> columnNames, List<Object[]> data) {
        super(GRID_DATA, name);
        if (columnNames != null) {
            this.columnNames.addAll(columnNames);
        }
        if (data != null) {
            this.data.addAll(data);
        }
    }

    public BinaryGridDataCommand() {
        this(null, null, null);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        // Write the name
        super.write(out);
        
        // Write the column names
        BinaryProtocol.writeInt(out, columnNames.size());
        for (String columnName : columnNames) {
            BinaryProtocol.writeString(out, columnName);
        }
        
        // Write the data
        BinaryProtocol.writeInt(out, data.size());
        for (Object[] row : data) {
            // Write the row length
            int rowLength = row != null ? row.length : 0;
            BinaryProtocol.writeInt(out, rowLength);
            
            // Write each cell
            if (row != null) {
                for (Object cell : row) {
                    writeCell(out, cell);
                }
            }
        }
    }

    @Override
    protected void read(InputStream in) throws IOException {
        // Read the name
        super.read(in);
        
        // Read the column names
        int columnCount = BinaryProtocol.readInt(in);
        columnNames.clear();
        for (int i = 0; i < columnCount; i++) {
            columnNames.add(BinaryProtocol.readString(in));
        }
        
        // Read the data
        int rowCount = BinaryProtocol.readInt(in);
        data.clear();
        for (int i = 0; i < rowCount; i++) {
            // Read the row length
            int rowLength = BinaryProtocol.readInt(in);
            
            // Read each cell
            Object[] row = new Object[rowLength];
            for (int j = 0; j < rowLength; j++) {
                row[j] = readCell(in);
            }
            
            data.add(row);
        }
    }

    /**
     * Write a cell value to the output stream
     */
    private void writeCell(OutputStream out, Object cell) throws IOException {
        if (cell == null) {
            // Null value has type code 0
            BinaryProtocol.writeByte(out, (byte) 0);
        } else if (cell instanceof String) {
            BinaryProtocol.writeByte(out, (byte) 1);
            BinaryProtocol.writeString(out, (String) cell);
        } else if (cell instanceof Integer) {
            BinaryProtocol.writeByte(out, (byte) 2);
            BinaryProtocol.writeInt(out, (Integer) cell);
        } else if (cell instanceof Long) {
            BinaryProtocol.writeByte(out, (byte) 3);
            BinaryProtocol.writeLong(out, (Long) cell);
        } else if (cell instanceof Float) {
            BinaryProtocol.writeByte(out, (byte) 4);
            BinaryProtocol.writeFloat(out, (Float) cell);
        } else if (cell instanceof Double) {
            BinaryProtocol.writeByte(out, (byte) 5);
            BinaryProtocol.writeDouble(out, (Double) cell);
        } else if (cell instanceof Boolean) {
            BinaryProtocol.writeByte(out, (byte) 6);
            BinaryProtocol.writeBoolean(out, (Boolean) cell);
        } else {
            // For unsupported types, convert to string
            BinaryProtocol.writeByte(out, (byte) 1);
            BinaryProtocol.writeString(out, cell.toString());
        }
    }

    /**
     * Read a cell value from the input stream
     */
    private Object readCell(InputStream in) throws IOException {
        byte type = BinaryProtocol.readByte(in);
        
        switch (type) {
            case 0: // null
                return null;
            case 1: // String
                return BinaryProtocol.readString(in);
            case 2: // Integer
                return BinaryProtocol.readInt(in);
            case 3: // Long
                return BinaryProtocol.readLong(in);
            case 4: // Float
                return BinaryProtocol.readFloat(in);
            case 5: // Double
                return BinaryProtocol.readDouble(in);
            case 6: // Boolean
                return BinaryProtocol.readBoolean(in);
            default:
                throw new IOException("Unsupported cell type: " + type);
        }
    }

    public List<String> getColumnNames() {
        return new ArrayList<>(columnNames);
    }

    public void setColumnNames(List<String> columnNames) {
        this.columnNames.clear();
        if (columnNames != null) {
            this.columnNames.addAll(columnNames);
        }
    }

    public List<Object[]> getData() {
        List<Object[]> result = new ArrayList<>(data.size());
        for (Object[] row : data) {
            result.add(row.clone());
        }
        return result;
    }

    public void setData(List<Object[]> data) {
        this.data.clear();
        if (data != null) {
            for (Object[] row : data) {
                this.data.add(row.clone());
            }
        }
    }

    public void addRow(Object[] row) {
        if (row != null) {
            this.data.add(row.clone());
        }
    }
} 