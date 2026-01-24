import java.io.IOException;
import java.net.*;
import java.util.List;

public class Host {

    private final String id;
    private final String ip;
    private final int port;
    private final String switchIP;
    private final int switchPort;

    private DatagramSocket socket;

    public Host(String id, Parser parser) throws IOException {
        this.id = id;

        Parser.DeviceInfo me = parser.getDevice(id);
        this.ip = me.ip;
        this.port = me.port;

        List<String> neighbors = parser.getConnections(id);
        if (neighbors.size() != 1) {
            throw new IllegalStateException("Host must have exactly one switch neighbor.");
        }

        String switchID = neighbors.get(0);
        Parser.DeviceInfo sw = parser.getDevice(switchID);
        this.switchIP = sw.ip;
        this.switchPort = sw.port;

        socket = new DatagramSocket(port);
        System.out.println("Host " + id + " listening on " + ip + ":" + port);
        System.out.println("Host " + id + "will connect to switch " + switchID + " at " + switchIP + " - " + switchPort);
    }

    public void listen() throws IOException {
        byte[] buf = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        while (true) {
            socket.receive(packet);
            Frame frame = Frame.fromBytes(packet.getData());

            System.out.println(id + " received: " + frame);
        }
    }

    public void sendFrame(String dstMAC, String payload) throws IOException {
        Frame f = new Frame(id, dstMAC, payload);
        byte[] data = f.toBytes();

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(switchIP),
                switchPort
        );

        socket.send(packet);
        System.out.println(id + " sent frame to " + dstMAC);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: Host <ID>");
            return;
        }

        String myID = args[0];
        Parser parser = new Parser("config.txt");

        Host host = new Host(myID, parser);
        host.listen();
    }
}
