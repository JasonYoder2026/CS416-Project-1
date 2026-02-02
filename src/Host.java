import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Scanner;
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

                    String msg = frame.getPayload();
                    String src = frame.getSrcMAC();
                    String dst = frame.getDstMAC();

                    // Print debug message if destination ID is different then Host's own ID
                    if(dst.equals(id)){
                        System.out.println(id + " received message from " + src + ": " + msg);
                    }
                    else {
                        System.out.println("DEBUG: Flood frame! Frame intended for " + dst + " but " + id + " has received it");
                    }

                    System.out.println("Enter destination and message (e.g., 'D hello') or q to quit:");
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
        this.switchPort = sw.port;

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
        Scanner scn = new Scanner(System.in);
        System.out.println("Enter destination and message (e.g., 'D hello') or q to quit:");
        while(true){
            if(scn.hasNextLine()){
                String line = scn.nextLine();
                if(line.equalsIgnoreCase("q")){
                    System.out.println("Shutting down Host: " + myID + "...");
                    break;
                }
                String[] parts = line.split(" ",2);

                if(parts.length == 2){
                    String destId = parts[0];
                    String message = parts[1];
                    host.sendFrame(destId,message);
                } else{
                    System.out.println("Invalid format! Use: <Destination MAC Address> <Message>");
                }
            }
        }
        host.es.shutdown();
    }
}
