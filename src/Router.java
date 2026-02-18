import java.io.IOException;
import java.net.*;
import java.util.*;

public class Router {
    private final String id;
    private final String realIP;
    private final int realPort;
    private DatagramSocket socket;
    private final Parser parser;

    private final Map<String, String> ipForwardingTable = new HashMap<>();
    //subnet/port -> direct neighbor device
    private final Map<String, String> subnetToNeighbor = new HashMap<>();

    public Router(String id, Parser parser) throws IOException {
        this.id = id;
        this.parser = parser;

        Parser.DeviceInfo thisDevice = parser.getDevice(id);

        this.realIP = thisDevice.ip;
        this.realPort = thisDevice.port;

        socket = new DatagramSocket(realPort);

        parsePorts();
        loadForwardingTable();
    }

    /**
     * Uses the router VIP to discover which subnet the ports belong to and which neighbor device is on that port
     */
    private void parsePorts() {
        List<String> myVips = parser.getVip(id);

        for (String vip : myVips) {
            String[] parts = vip.split("\\.");
            String subnet = parts[0];
            String portName = parts[1];

            List<String> neighbors = parser.getConnections(portName);
            if (neighbors.isEmpty()) {
                System.err.println("[" + id + "] No neighbor found on port " + portName);
                continue;
            }
            String neighborId = resolveDeviceId(neighbors.get(0));
            subnetToNeighbor.put(subnet, neighborId);
            System.out.println("[" + id + "] subnet " + subnet + " -> neighbor " + neighborId + " (via port " + portName + ")");
        }

    }

    /**
     * Resolves link endpoint (R2L) to actual device ID
     */
    private String resolveDeviceId(String endpoint) {
        if (parser.getDevice(endpoint) != null) return endpoint;
        for (int len = endpoint.length() - 1; len > 0; len--) {
            String candidate = endpoint.substring(0, len);
            if (parser.getDevice(candidate) != null) return candidate;
        }
        return endpoint;
    }

    /**
     * Manually load forwarding table
     */
    private void loadForwardingTable() {
        if (id.equals("R1")) {
            ipForwardingTable.put("net1", "R1L");
            ipForwardingTable.put("net2", "R1R");
            ipForwardingTable.put("net3", "R2L");

        } else if (id.equals("R2")) {
            ipForwardingTable.put("net1", "R1R");
            ipForwardingTable.put("net2", "R2L");
            ipForwardingTable.put("net3", "R2R");
        }
    }

    /**
     * Listen for incoming frames
     */
    private void listen() {
        System.out.println("[" + id + "] Listening on " + realIP + ":" + realPort);
        byte[] buf = new byte[4096];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                Frame frame = Frame.fromBytes(Arrays.copyOf(packet.getData(), packet.getLength()));
                System.out.println("[" + id + "] RECEIVED:   " + frame);
                forwardFrame(frame);
            } catch (IOException e) {
                System.err.println("[" + id + "] Error: " + e.getMessage());
            }
        }
    }

    /**
     * Forward frame to next hop/subnet
     */
    private void forwardFrame(Frame frame) throws IOException {
        String dstSubnet = frame.getDstIP().split("\\.")[0];

        String entry = ipForwardingTable.get(dstSubnet);
        if (entry == null) {
            System.err.println("[" + id + "] No route for subnet: " + dstSubnet + "' — dropping.");
            return;
        }

        String nextHopId = entry.contains(".") ? entry.split("\\.")[1] : entry;

        frame.chgSrcMAC(id);
        frame.chgDstMAC(nextHopId);

        System.out.println("[" + id + "] Forwarding " + nextHopId + " to " + dstSubnet);

        Parser.DeviceInfo neighbor = parser.getDevice(nextHopId);
        if (neighbor == null) {
            System.err.println("[" + id + "] Unknown next-hop device: '" + nextHopId + "' — dropping.");
            return;
        }

        byte[] data = frame.toBytes();
        DatagramPacket outPacket = new DatagramPacket(
                data, data.length, InetAddress.getByName(neighbor.ip), neighbor.port
        );
        socket.send(outPacket);
    }

    static void main(String args[]) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: Switch <ID>");
            return;
        }

        String myID = args[0];
        Parser parser = new Parser("config.txt");

        Router router = new Router(myID, parser);
        router.listen();
    }
}
