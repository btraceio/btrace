package org.opensolaris.os.dtrace;

public abstract class ConsumerAdapter implements ConsumerListener {
    public abstract void consumerStarted(ConsumerEvent ce);

    public abstract void consumerStopped(ConsumerEvent ce);


    public abstract void dataReceived(DataEvent de);

    public abstract void dataDropped(DropEvent de);

    public void errorEncountered(ErrorEvent ee) throws ConsumerException {}
}
