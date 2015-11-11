/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Jaroslav Bachorik
 */
public class SetSettingsCommand extends Command {
    private final Map<String, Object> params;
    public SetSettingsCommand(Map<String, ?> params) {
        super(SET_PARAMS);
        this.params = params != null ? new HashMap<>(params) : new HashMap<String, Object>();
    }

    protected SetSettingsCommand() {
        this(null);
    }

    public Map<String, Object> getParams() {
        return params;
    }

    @Override
    protected void write(ObjectOutput out) throws IOException {
        out.writeInt(params.size());
        for(Map.Entry<String, ?> e : params.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeObject(e.getValue());
        }
    }

    @Override
    protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
        int size = in.readInt();
        for(int i=0;i<size;i++) {
            String k = in.readUTF();
            Object v = in.readObject();

            params.put(k, v);
        }
    }

}
