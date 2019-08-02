package org.opensolaris.os.dtrace;

import java.util.EventObject;

public class DropEvent extends EventObject {
    public DropEvent(Object source) {
        super(source);
    }

    public Drop getDrop() {
        return null;
    }
}
