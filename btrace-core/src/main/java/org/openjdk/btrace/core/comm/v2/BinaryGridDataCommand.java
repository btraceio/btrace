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
    private static final ScalarEncoding SCALAR =
        new ScalarEncoding((byte)0, (byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6);

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
                    SCALAR.writeValue(out, cell);
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
                row[j] = SCALAR.readValue(in);
            }
            
            data.add(row);
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
