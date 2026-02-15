import java.io.IOException;
import java.net.*;
import java.util.*;

public class Switch {

    private final String id;
    private final String ip;
    private final int port;

    private DatagramSocket socket;

    private final Map<String, Integer> switchTable = new HashMap<>();

    private final Map<Integer, Parser.DeviceInfo> ports = new HashMap<>();

    public Switch(String id, Parser parser) throws IOException {
        this.id = id;

        Parser.DeviceInfo me = parser.getDevice(id);
        List<String> myMAC = parser.getMac(id);
        this.ip = me.ip;
        this.port = me.port;

        socket = new DatagramSocket(port);

        // Load neighbors and assign them port numbers
        List<String> neighbors = parser.getConnections(id);
        int portNum = 1;
        for (String n : neighbors) {
            ports.put(portNum++, parser.getDevice(n));
        }

        printPhysicalAddress();
    }

    private void printPhysicalAddress() {
        System.out.println("Switch " + id + " running at " + ip + ":" + port);
    }

    private void printSwitchTable() {
        System.out.println("--- Switch Table (" + id + ") ---");
        for (var entry : switchTable.entrySet()) {
            System.out.println("MAC " + entry.getKey() + " → Port " + entry.getValue() + "\n");
        }
    }

    private void learn(String srcMAC, int incomingPort) {
        if (!switchTable.containsKey(srcMAC)) {
            switchTable.put(srcMAC, incomingPort);
            System.out.println("Learned MAC " + srcMAC + " on port " + incomingPort);
            printSwitchTable();
        }
    }

    private void forward(Frame frame, int incomingPort) throws IOException {
        String dst = frame.getDstMAC();

        if (switchTable.containsKey(dst)) {
            int outPort = switchTable.get(dst);
            sendFrame(frame, outPort);
        } else {
            flood(frame, incomingPort);
        }
    }

    private void sendFrame(Frame frame, int portNum) throws IOException {
        Parser.DeviceInfo dev = ports.get(portNum);
        byte[] data = frame.toBytes();

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(dev.ip),
                dev.port
        );

        socket.send(packet);
        System.out.println(id + " forwarded frame to " + dev.id + " via port " + portNum);
    }

    private void flood(Frame frame, int incomingPort) throws IOException {
        for (var entry : ports.entrySet()) {
            int portNum = entry.getKey();
            if (portNum == incomingPort) continue;
            sendFrame(frame, portNum);
        }
    }

    public void listen() throws IOException {
        byte[] buf = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        while (true) {
            socket.receive(packet);
            Frame frame = Frame.fromBytes(packet.getData());

            int incomingPort = findIncomingPort(packet.getAddress(), packet.getPort());
            if (incomingPort == -1) {
                System.err.println("Unknown incoming port for packet");
                continue;
            }

            learn(frame.getSrcMAC(), incomingPort);
            forward(frame, incomingPort);
        }
    }

    private int findIncomingPort(InetAddress addr, int port) {
        for (var entry : ports.entrySet()) {
            Parser.DeviceInfo dev = entry.getValue();
            if (dev.ip.equals(addr.getHostAddress()) && dev.port == port) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: Switch <ID>");
            return;
        }

        String myID = args[0];
        Parser parser = new Parser("config.txt");

        Switch sw = new Switch(myID, parser);
        sw.listen();
    }
}
