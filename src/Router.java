import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Router {

    private static final int DV_INTERVAL_SEC = 5;
    private static final int INFINITY = 1000000;

    private final String id;
    private final String realIP;
    private final int realPort;
    private final DatagramSocket socket;
    private final Parser parser;

    //direct neighbor devices
    private final Map<String, String> subnetToNeighbor = new HashMap<>();
    //router neighbor devices
    private final List<String> routerNeighbors = new ArrayList<>();

    private static class DVEntry {
        String nextHop;
        int    cost;
        DVEntry(String nextHop, int cost) { this.nextHop = nextHop; this.cost = cost; }
    }

    private final Map<String, DVEntry> dvTable = new HashMap<>();
    private final Map<String, Map<String, Integer>> neighborDVs = new HashMap<>();

    public Router(String id, Parser parser) throws IOException {
        this.id = id;
        this.parser = parser;

        Parser.DeviceInfo thisDevice = parser.getDevice(id);

        this.realIP = thisDevice.ip;
        this.realPort = thisDevice.port;

        socket = new DatagramSocket(realPort);

        parsePorts();
        initDVTable();
        startDVBroadcast();
    }

    /**
     * Uses the router VIP to discover which subnet the ports belong to and which neighbor device is on that port
     */
    private void parsePorts() {
        List<String> myVips = parser.getVip(id);

        for (String vip : myVips) {
            String subnet = vip.split("\\.")[0];

            for (String neighborEndpoint : parser.getConnections(id)) {
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

        for (String endpoint : parser.getConnections(id)) {
            String neighborId = resolveDeviceId(endpoint);
            if (neighborId.startsWith("R") && !routerNeighbors.contains(neighborId)) {
                routerNeighbors.add(neighborId);
                System.out.println("[" + id + "] router neighbor: " + neighborId);
            }
        }

    }

    /**
     * Seed every direct subnet with cost 0 and no next hop
     */
    private void initDVTable() {
        for (String vip : parser.getVip(id)) {
            String subnet = vip.split("\\.")[0];
            dvTable.put(subnet, new DVEntry(null, 0));
            System.out.println("[" + id + "] DV init: " + subnet + " cost=0 (direct)");
        }
    }

    /**
     * Schedule periodic DV broadcasts. First broadcast on start.
     */
    private void startDVBroadcast() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread scheduledBroadcast = new Thread(r, id + "-dv-broadcast");
            scheduledBroadcast.setDaemon(true);
            return scheduledBroadcast;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                broadcastDV();
            } catch (IOException e) {
                System.err.println("[" + id + "] DV broadcast error: " + e.getMessage());
            }
        }, 0, DV_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    /**
     * Serialize the DV table
     */
    private synchronized String serializeDV() {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, DVEntry> e: dvTable.entrySet()) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue().cost);
        }
        return sb.toString();
    }

    /**
     * Parses incoming DVs
     */
    private static Map<String, Integer> parseDVPayload(String payload) {
        Map<String, Integer> dv = new HashMap<>();
        for (String token : payload.split(",")) {
            String[] kv = token.split(":");
            if (kv.length == 2) {
                dv.put(kv[0], Integer.parseInt(kv[1]));
            }
        }
        return dv;
    }

    /**
     * Sends out DV
     */
    private void broadcastDV() throws IOException {
        String payload = serializeDV();
        for (String neighbor: routerNeighbors) {
            Frame dvFrame = Frame.createRoutingUpdate(id, neighbor, payload);
            sendFrame(dvFrame, neighbor);
            //System.out.println("[" + id + "] DV -> " + neighbor + " : " + payload);
        }
    }

    /**
     * Updates DV when neighboring router sends DV
     * If any entry improves, re-broadcast
     */

    private synchronized void processDVUpdate(String neighborId, String payload) {
        Map<String, Integer> neighborDV = parseDVPayload(payload);
        neighborDVs.put(neighborId, neighborDV);

        boolean improved = false;

        for (Map.Entry<String, Integer> e : neighborDV.entrySet()) {
            String subnet = e.getKey();
            int neighborCost = e.getValue();

            if (neighborCost >= INFINITY) continue;

            int costViaNeighbor = 1 + neighborCost;

            DVEntry currentDV = dvTable.get(subnet);
            if (currentDV == null || costViaNeighbor < currentDV.cost) {
                dvTable.put(subnet, new DVEntry(neighborId, costViaNeighbor));
//                System.out.println("[" + id + "] DV update: " + subnet
//                        + " via " + neighborId
//                        + " cost=" + costViaNeighbor);
                improved = true;
            }
        }

        if (improved) {
            try {
                broadcastDV();
            } catch (IOException ex) {
                System.err.println("[" + id + "] triggered-update error: " + ex.getMessage());
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
                //check for packet type
                if (frame.isRoutingUpdate()) {
                    //System.out.println("[" + id + "] DV received from " + frame.getSrcMAC());
                    processDVUpdate(frame.getSrcMAC(), frame.getPayload());
                } else {
                    System.out.println("[" + id + "] RECEIVED: " + frame);
                    forwardFrame(frame);
                }
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

        DVEntry entry = dvTable.get(dstSubnet);
        if (entry == null) {
            System.err.println("[" + id + "] No route to subnet: " + dstSubnet + " — dropping.");
            return;
        }

        frame.chgSrcMAC(id);
        frame.chgDstMAC(entry.nextHop);
        sendFrame(frame, entry.nextHop);

        System.out.println("[" + id + "] Forwarding packet for " + entry.nextHop + " to " + dstSubnet);
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

        //System.out.println("[" + id + "] SENT to " + deviceId + ": " + frame);
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
