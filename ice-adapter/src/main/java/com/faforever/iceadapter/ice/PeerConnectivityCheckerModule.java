package com.faforever.iceadapter.ice;

import static com.faforever.iceadapter.debug.Debug.debug;

import com.google.common.primitives.Longs;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * Periodically sends echo requests via the ICE data channel and initiates a reconnect after timeout
 * ONLY THE OFFERING ADAPTER of a connection will send echos and reoffer.
 */
public class PeerConnectivityCheckerModule {

    private static final int ECHO_INTERVAL = 1000;

    private final PeerIceModule ice;
    private volatile boolean running = false;
    private volatile Thread checkerThread;

    private final AtomicReference<Float> averageRTT = new AtomicReference<>(0.0f);

    public float getAverageRTT() {
        return averageRTT.get(); // Extract the value directly
    }

    private final AtomicLong lastPacketReceived = new AtomicLong(System.currentTimeMillis());

    public long getLastPacketReceived() {
        return lastPacketReceived.get(); // Extract the value directly
    }

    @Getter
    private long echosReceived = 0;

    @Getter
    private long invalidEchosReceived = 0;

    public PeerConnectivityCheckerModule(PeerIceModule ice) {
        this.ice = ice;
    }

    synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        log.debug("Starting connectivity checker for peer {}", ice.getPeer().getRemoteId());

        averageRTT.set(0.0f);
        lastPacketReceived.set(System.currentTimeMillis());

        checkerThread = new Thread(this::checkerThread, getThreadName());
        checkerThread.setUncaughtExceptionHandler(
                (t, e) -> log.error("Thread {} crashed unexpectedly", t.getName(), e));
        checkerThread.start();
    }

    private String getThreadName() {
        return "connectivityChecker-" + ice.getPeer().getRemoteId();
    }

    synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (checkerThread != null) {
            checkerThread.interrupt();
            checkerThread = null;
        }
    }

    /**
     * an echo has been received, RTT and last_received will be updated
     * @param data
     * @param offset
     * @param length
     */
    void echoReceived(byte[] data, int offset, int length) {
        echosReceived++;

        if (length != 9) {
            log.trace("Received echo of wrong length, length: {}", length);
            invalidEchosReceived++;
        }

        int rtt =
                (int) (System.currentTimeMillis() - Longs.fromByteArray(Arrays.copyOfRange(data, offset + 1, length)));
        averageRTT.updateAndGet(current -> current == 0 ? rtt : current * 0.8f + rtt * 0.2f);

        lastPacketReceived.set(System.currentTimeMillis());

        debug().peerConnectivityUpdate(ice.getPeer());
        //      System.out.printf("Received echo from %d after %d ms, averageRTT: %d ms", ice.getPeer().getRemoteId(),
        // rtt, (int) averageRTT);
    }

    private void checkerThread() {
        while (!Thread.currentThread().isInterrupted()) {
            log.trace("Running connectivity checker");

            byte[] data = new byte[9];
            data[0] = 'e';

            // Copy current time (long, 8 bytes) into array after leading prefix indicating echo
            System.arraycopy(Longs.toByteArray(System.currentTimeMillis()), 0, data, 1, 8);

            ice.sendViaIce(data, 0, data.length);

            debug().peerConnectivityUpdate(ice.getPeer());

            try {
                Thread.sleep(ECHO_INTERVAL);
            } catch (InterruptedException e) {
                log.warn(
                        "{} (sleeping checkerThread) was interrupted",
                        Thread.currentThread().getName());
                Thread.currentThread().interrupt();
                return;
            }

            if (System.currentTimeMillis() - getLastPacketReceived() > 10000) {
                log.warn(
                        "Didn't receive any answer to echo requests for the past 10 seconds from {}, aborting connection",
                        ice.getPeer().getRemoteLogin());
                new Thread(ice::onConnectionLost).start();
                return;
            }
        }

        log.info("{} stopped gracefully", Thread.currentThread().getName());
    }
}
