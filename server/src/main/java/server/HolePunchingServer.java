package server;

import net.HolePunchingMessage;
import java.util.HashMap;
import java.util.Map;

public class HolePunchingServer {

    public static volatile int port = 51239;
    public static Map<Integer, Integer> ports = new HashMap<>();

    public static synchronized int onPlayerConnected(Player player) {
        int newPort = port++;
        ports.put(player.getId(), newPort);
        return newPort;
    }

    public static synchronized void connect(Player player1, Player player2) {
        // Null check for player1 and player2
        if (player1 == null || player2 == null) {
            throw new IllegalArgumentException("Players cannot be null");
        }

        // Null check for player1's socket
        if (player1.getSocket() == null || player2.getSocket() == null) {
            throw new IllegalStateException("Player's socket cannot be null");
        }

        // Null check for ports
        Integer port1 = ports.get(player1.getId());
        Integer port2 = ports.get(player2.getId());
        
        if (port1 == null || port2 == null) {
            throw new IllegalStateException("Player's port cannot be null");
        }

        // Send the HolePunchingMessage to both players
        player1.send(new HolePunchingMessage(player2.getId(), player2.getSocket().getInetAddress().getHostAddress(), port2));
        player2.send(new HolePunchingMessage(player1.getId(), player1.getSocket().getInetAddress().getHostAddress(), port1));
    }
}
