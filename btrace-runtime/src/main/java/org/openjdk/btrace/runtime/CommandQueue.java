package org.openjdk.btrace.runtime;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscChunkedArrayQueue;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.MessageCommand;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

final class CommandQueue {
    private final MpscChunkedArrayQueue<Command> queue;
    private final AtomicLong droppedCommands = new AtomicLong();

    CommandQueue(int capacity) {
        queue = new MpscChunkedArrayQueue<>(capacity);
    }

    public boolean addAll(Collection<? extends Command> c) {
        boolean rslt = true;
        for (Command cmd : c) {
            if (!enqueue(cmd)) {
                rslt &= false;
            }
        }
        return rslt;
    }

    public void drain(MessagePassingQueue.Consumer<Command> c, MessagePassingQueue.WaitStrategy wait, MessagePassingQueue.ExitCondition exit) {
        queue.drain(e -> {
            long dropped = droppedCommands.get();
            if (dropped > 0) {
                c.accept(new MessageCommand("Dropped " + dropped + " commands"));
                droppedCommands.addAndGet(-dropped);
            }
            c.accept(e);
        }, wait, exit);
    }

    public boolean enqueue(Command cmd) {
        int backoffCntr = 0;
        long tsCutOff = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1);
        while (!Thread.interrupted() && !queue.relaxedOffer(cmd)) {
            if (backoffCntr < 3000) {
                Thread.yield();
            } else if (backoffCntr < 3100) {
                LockSupport.parkNanos(1_000_000);
            }
            backoffCntr++;
            if (System.nanoTime() > tsCutOff) {
                droppedCommands.incrementAndGet();
                return false;
            }
        }
        return true;
    }

    public void clear() {
        queue.clear();
        droppedCommands.set(0);
    }
}
