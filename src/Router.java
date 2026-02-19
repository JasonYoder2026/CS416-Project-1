import java.io.IOException;
import java.net.*;
import java.util.*;

public class Router {
    private final String id;
    private final String realIP;
    private final int realPort;
    private final DatagramSocket socket;
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
            String subnet = vip.split("\\.")[0];

            // Find the neighbor that shares this subnet
            List<String> allNeighbors = parser.getConnections(id);
            for (String neighborEndpoint : allNeighbors) {
                String neighborId = resolveDeviceId(neighborEndpoint);
                List<String> neighborVips = parser.getVip(neighborId);
                if (neighborVips == null) continue;

                for (String neighborVip : neighborVips) {
                    if (neighborVip.split("\\.")[0].equals(subnet)) {
                        subnetToNeighbor.put(subnet, neighborId);
                        System.out.println("[" + id + "] subnet " + subnet + " -> neighbor " + neighborId);
                        break;
                    }
                }
            }
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
            //ipForwardingTable.put("net1", "net1.R1");
            //ipForwardingTable.put("net2", "net2.R1");
            ipForwardingTable.put("net3", "R2");

        } else if (id.equals("R2")) {
            //ipForwardingTable.put("net1", "net2.R1");
            //ipForwardingTable.put("net2", "net2.R2");
            ipForwardingTable.put("net1", "R1");
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
        String srcSubnet = frame.getSrcIP().split("\\.")[0];

        if (srcSubnet.equals(dstSubnet)) {
            System.out.println("[" + id + "] Dropping intra-subnet packet (src and dst on same subnet)");
            return;
        }

        if (subnetToNeighbor.containsKey(dstSubnet)) {
            String neighborId = subnetToNeighbor.get(dstSubnet);

            String dstId = frame.getDstIP().split("\\.")[1];

            frame.chgSrcMAC(id);
            frame.chgDstMAC(dstId);

            sendFrame(frame, neighborId);
            return;
        }

        String nextHopId = ipForwardingTable.get(dstSubnet);

        if (nextHopId == null) {
            System.err.println("No route for subnet: " + dstSubnet);
            return;
        }

        frame.chgSrcMAC(id);
        frame.chgDstMAC(nextHopId);
        sendFrame(frame, nextHopId);

        System.out.println("[" + id + "] Forwarding packet for " + nextHopId + " to " + dstSubnet);


    }

    private void sendFrame(Frame frame, String deviceId) throws IOException {
        Parser.DeviceInfo device = parser.getDevice(deviceId);

        if (device == null) {
            System.err.println("[" + id + "] Unknown device: " + deviceId + " — dropping.");
            return;
        }

        byte[] data = frame.toBytes();

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(device.ip),
                device.port
        );

        socket.send(packet);

        System.out.println("[" + id + "] SENT to " + deviceId + ": " + frame);
    }

    static void main(String[] args) throws IOException {
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
