import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

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
                    String dstMAC = frame.getDstMAC();
                    String dstIP = frame.getDstIP();

                    // Print debug message if destination ID is different then Host's own ID
                    if(dstMAC.equals(myMac) && dstIP.equals(ip)){
                        System.out.println(id + " received message from " + src + ": " + msg);
                    }
                    else {
                        System.out.println("DEBUG: Flood frame! Frame intended for " + dstMAC + " but " + id + " has received it");
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
        String dstIP;
        String payload;

        SendThread(String dstIP,String dstMAC, String payload){
            this.dstIP = dstIP;
            this.dstMAC = dstMAC;
            this.payload = payload;
        }

        @Override
        public void run() {

            try {
                Frame f = new Frame(ip, dstIP, id, dstMAC, payload);
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
    private final String subnet;
    //private final String gatewayRouter;
    private final List<String> myMac;
    private final int port;
    private final String switchIP;
    private final String switchMac;
    private final int switchPort;
    private DatagramSocket socket;
    private ExecutorService es;

    public Host(String id, Parser parser) throws IOException {
        this.id = id;

        Parser.DeviceInfo me = parser.getDevice(id);
        this.myMac = parser.getMac(id);
        this.ip = me.ip;
        this.subnet = ip.split(Pattern.quote("."))[0];
        this.port = me.port;

        List<String> neighbors = parser.getConnections(id);
        if (neighbors.size() != 1) {
            throw new IllegalStateException("Host must have exactly one switch neighbor.");
        }

        String switchID = neighbors.get(0);
        Parser.DeviceInfo sw = parser.getDevice(switchID);
        this.switchIP = sw.ip;
        this.switchPort = sw.port;
        this.switchMac = sw.macs.get(0);
        System.out.println(switchMac);

        socket = new DatagramSocket(port);
        es = Executors.newFixedThreadPool(8);
        listen();
        System.out.println("Host " + id + " listening on " + ip + ":" + port);
        System.out.println("Host " + id + " will connect to switch " + switchID + " at " + switchIP + " - " + switchPort);
    }

    private void listen() throws IOException {
        es.submit(new ListenThread());
    }

    public void sendFrame(String destIP, String payload) throws IOException {

        if(destIP.split(Pattern.quote("."))[0] == subnet) {
            es.submit(new SendThread(switchMac,switchIP,payload));
        }else{
            //es.submit(new SendThread(switchMac,switchIP,payload)); Change to gateway router
        }
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
                    String destIP = parts[0];
                    String message = parts[1];
                    host.sendFrame(destIP,message);
                } else{
                    System.out.println("Invalid format! Use: <Destination MAC Address> <Message>");
                }
            }
        }
        host.es.shutdown();
    }
}
