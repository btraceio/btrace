package com.sun.btrace.shared;

import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.runtime.Level;
import com.sun.btrace.runtime.Location;
import com.sun.btrace.runtime.OnMethod;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public final class InstrumentationRecipe {
    static final class CutPoint {
        final OnMethod def;

        public CutPoint(OnMethod def) {
            this.def = def;
        }
    }

    private final CutPoint[] cutpoints;
    private final byte[] bytecode;

    InstrumentationRecipe(CutPoint[] cutpoints, byte[] bytecode) {
        this.cutpoints = cutpoints;
        this.bytecode = bytecode;
    }

    public static InstrumentationRecipe from(InputStream is) throws IOException {
        ObjectInputStream ois = new ObjectInputStream(is);
        int numCutpoints = ois.readInt();
        CutPoint[] cutpoints = new CutPoint[numCutpoints];
        for (int i = 0; i < numCutpoints; i++) {
            cutpoints[i] = readCutPoint(ois);
        }
        int bcLen = ois.readInt();
        byte[] data = new byte[bcLen];
        ois.readFully(data);

        return new InstrumentationRecipe(cutpoints, data);
    }

    public static InstrumentationRecipe from(byte[] data) throws IOException {
        return from(new ByteArrayInputStream(data));
    }

    public void to(OutputStream os) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeInt(cutpoints.length);
        for (CutPoint cp : cutpoints) {
            writeCutPoint(cp, oos);
        }
        oos.write(bytecode);
    }

    private static CutPoint readCutPoint(ObjectInputStream ois) throws IOException {
        OnMethod om = new OnMethod();
        om.setClassNameParameter(ois.readInt());
        om.setDurationParameter(ois.readInt());
        om.setMethodParameter(ois.readInt());
        om.setReturnParameter(ois.readInt());
        om.setSelfParameter(ois.readInt());
        om.setTargetInstanceParameter(ois.readInt());
        om.setTargetMethodOrFieldParameter(ois.readInt());
        om.setSamplerMean(ois.readInt());

        om.setMethodFqn(ois.readBoolean());
        om.setTargetMethodOrFieldFqn(ois.readBoolean());

        om.setMethod(ois.readUTF());
        om.setClazz(ois.readUTF());
        om.setTargetName(ois.readUTF());
        om.setType(ois.readUTF());
        om.setTargetDescriptor(ois.readUTF());

        String level = ois.readUTF();
        om.setLevel(Level.fromString(level));

        Location loc = new Location();
        loc.setClazz(ois.readUTF());
        loc.setField(ois.readUTF());
        loc.setMethod(ois.readUTF());
        loc.setType(ois.readUTF());
        loc.setLine(ois.readInt());
        loc.setValue(Kind.valueOf(ois.readUTF()));
        loc.setWhere(Where.valueOf(ois.readUTF()));

        om.setLocation(loc);

        om.setSamplerKind(Sampled.Sampler.valueOf(ois.readUTF()));

        return new CutPoint(om);
    }

    private static void writeCutPoint(CutPoint cp, ObjectOutputStream oos) throws IOException {
        oos.writeInt(cp.def.getClassNameParameter());
        oos.writeInt(cp.def.getDurationParameter());
        oos.writeInt(cp.def.getMethodParameter());
        oos.writeInt(cp.def.getReturnParameter());
        oos.writeInt(cp.def.getSelfParameter());
        oos.writeInt(cp.def.getTargetInstanceParameter());
        oos.writeInt(cp.def.getTargetMethodOrFieldParameter());
        oos.writeInt(cp.def.getSamplerMean());

        oos.writeBoolean(cp.def.isMethodFqn());
        oos.writeBoolean(cp.def.isTargetMethodOrFieldFqn());

        oos.writeUTF(cp.def.getMethod());
        oos.writeUTF(cp.def.getClazz());
        oos.writeUTF(cp.def.getTargetName());
        oos.writeUTF(cp.def.getType());
        oos.writeUTF(cp.def.getTargetDescriptor());

        oos.writeUTF(cp.def.getLevel().getValue().toString());

        Location loc = cp.def.getLocation();
        oos.writeUTF(loc.getClazz());
        oos.writeUTF(loc.getField());
        oos.writeUTF(loc.getMethod());
        oos.writeUTF(loc.getType());
        oos.writeInt(loc.getLine());
        oos.writeUTF(loc.getValue().name());
        oos.writeUTF(loc.getWhere().name());

        oos.writeUTF(cp.def.getSamplerKind().name());
    }
}
