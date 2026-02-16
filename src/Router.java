import java.io.IOException;
import java.net.*;
import java.util.*;

public class Router {
    private final String id;
    private final String ip;
    private final int port;

    private DatagramSocket socket;

    private final Map<String, String> ipForwardingTable = new HashMap<>();
    private final Map<String, String> ports = new HashMap<>();

    public Router(String id, Parser parser) throws IOException {
        this.id = id;

        Parser.DeviceInfo thisDevice = parser.getDevice(id);

        this.ip = thisDevice.ip;
        this.port = thisDevice.port;

        socket = new DatagramSocket(port);

        parsePorts();
        loadIpForwarding();
    }

    private void parsePorts() {

    }

    private void loadIpForwarding() {

    }

    private void listen() {

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
