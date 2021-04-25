package org.openjdk.btrace.core;

public interface BTraceContext {
    BTraceContext INSTANCE = new BTraceContext() {
        @Override
        public void sendMsg(String msg) {
            BTraceRuntime.sendMessage(msg);
        }

        @Override
        public String deadlocksStr(boolean stackTrace) {
            return BTraceRuntime.deadlocksStr(stackTrace);
        }
    };

    String deadlocksStr(boolean stackTrace);
    void sendMsg(String msg);
}
