package data;

import lombok.Data;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class ForgedAlliancePeer {

    private boolean connected = false;
    public final String remoteAddress;
    public final int remotePort;
    public final int remoteId;
    public final String remoteUsername;
    public final Offerer offerer;

    public int echoRequestsSent;
    private final AtomicLong lastPacketReceived = new AtomicLong(System.currentTimeMillis());
    
    public long getLastPacketReceived() {
        return lastPacketReceived.get(); // Extract the value directly
    }

    public final Queue<Integer> latencies = new LinkedList<>();

    public long lastConnectionRequestSent = 0;

    public void clearLatencyHistory() {
        synchronized (latencies) {
            latencies.clear();
        }
    }

    public int addLatency(int lat) {
        lastPacketReceived.set(System.currentTimeMillis());

        synchronized (latencies) {
            latencies.add(lat);
            if (latencies.size() > 25) {
                latencies.remove();
            }
        }
        return getAverageLatency();
    }

    public int getAverageLatency() {
        synchronized (latencies) {
            return (int) latencies.stream().mapToInt(Integer::intValue).average().orElse(0);
        }
    }

    public int getJitter() {
        int averageLatency = getAverageLatency();
        synchronized (latencies) {
            int maxLatency = latencies.stream().mapToInt(Integer::intValue).max().orElse(0);
            int minLatency = latencies.stream().mapToInt(Integer::intValue).min().orElse(0);
            return Math.max(maxLatency - averageLatency, averageLatency - minLatency);
        }
    }

    public boolean isQuiet() {
        return System.currentTimeMillis() - getLastPacketReceived() > 5000;
    }

    public enum Offerer {
        REMOTE, LOCAL
    }

}
