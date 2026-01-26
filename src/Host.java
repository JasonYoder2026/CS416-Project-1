import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Host {

    class ListenThread implements Runnable{
        @Override
        public void run() {

            byte[] buf = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (true) {
                try {
                    socket.receive(packet);
                    Frame frame = Frame.fromBytes(packet.getData());
                    System.out.println(id + " received: " + frame);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class SendThread implements Runnable{

        String dstMAC;
        String payload;

        SendThread(String dstMAC, String payload){
            this.dstMAC = dstMAC;
            this.payload = payload;
        }

        @Override
        public void run() {

            try {
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
            }catch (Exception e){
                e.printStackTrace();
            }

        }
    }

    private final String id;
    private final String ip;
    private final int port;
    private final String switchIP;
    private final int switchPort;
    private DatagramSocket socket;
    private ExecutorService es;

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
        this.switchPort = 5001; //Changes this to the port because i set run config to be A and changed to send to myself switch back to sw.port

        socket = new DatagramSocket(port);
        es = Executors.newFixedThreadPool(8);
        listen();
        System.out.println("Host " + id + " listening on " + ip + ":" + port);
        System.out.println("Host " + id + " will connect to switch " + switchID + " at " + switchIP + " - " + switchPort);
    }

    private void listen() throws IOException {
        es.submit(new ListenThread());
    }

    public void sendFrame(String dstMAC, String payload) throws IOException {
        es.submit(new SendThread(dstMAC,payload));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: Host <ID>");
            return;
        }

        String myID = args[0];
        Parser parser = new Parser("config.txt");

        Host host = new Host(myID, parser);

        //WILL WANT TO CREATE THE INTERFACE HERE TO LET THE USER TYPE WHAT THEY WANT TO SEND AND THEN CALL SEND
        host.sendFrame("A","Hello!");
    }
}
