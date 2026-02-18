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

                    // Print debug message if destination ID is different then Host's own ID
                    if(dstMAC.equals(macAddress) ){
                        System.out.println(macAddress + " received message from " + src + ": " + msg);
                    }
                    else {
                        System.out.println("DEBUG: Flood frame! Frame intended for " + dstMAC + " but " + macAddress + " has received it");
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
                Frame f = new Frame(macAddress,dstMAC, virtualIP,dstIP, payload);
                System.out.println(f.toString());
//                System.out.println("Source: " + dstMAC);
//                System.out.println("Dst Mac:" + macAddress);
//                System.out.println("Source IP: " + virtualIP);
//                System.out.println("Dest IP: " +dstIP);
//                System.out.println(payload);

                byte[] data = f.toBytes();

                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        InetAddress.getByName(switchIP),
                        switchPort
                );
                socket.send(packet);
                System.out.println(macAddress + " sent frame to " + dstMAC);
            }catch (Exception e){
                e.printStackTrace();
            }

        }
    }

    private final String macAddress; // misspelled address 'adress' initially
    private final String ip;
    private final String subnet;
    //private final String gatewayRouter;
    //private final List<String> myMac;
    private final int port;
    private final String switchIP;
    private final String switchMac;
    private final int switchPort;
    private DatagramSocket socket;
    private ExecutorService es;
    private final String virtualIP;
    private final String gatewayMAC;

    public Host(String id, Parser parser) throws IOException {
        this.macAddress = id;

        List<String> myVIP = parser.getVip(id);
        this.virtualIP = myVIP.getFirst();
        this.subnet = this.virtualIP.split("\\.")[0];

        String gatewayVIP = parser.getGateway(id);
        this.gatewayMAC = gatewayVIP.split("\\.")[1];

        Parser.DeviceInfo me = parser.getDevice(id);
        //this.myMac = parser.getMac(id);
        this.ip = me.ip;
        this.port = me.port;

        List<String> neighbors = parser.getConnections(id);
        if (neighbors.size() != 1) {
            throw new IllegalStateException("Host must have exactly one switch neighbor.");
        }


        String switchID = neighbors.getFirst();
        Parser.DeviceInfo sw = parser.getDevice(switchID);
        this.switchIP = sw.ip;
        this.switchPort = sw.port;
        // macs must have gotten renamed to vips
        this.switchMac = sw.vips.getFirst();
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

    public void sendFrame(String destVIP, String payload) throws IOException {
        String destSubnet = destVIP.split("\\.")[0];
        String nextHopMAC;

        if (destSubnet.equals(this.subnet)) {
            nextHopMAC = destVIP.split("\\.")[1];
        } else {
            nextHopMAC = this.gatewayMAC;
        }
        System.out.println(nextHopMAC);
        es.submit(new SendThread(destVIP, nextHopMAC, payload));
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
        System.out.println("Enter destination and message (e.g., 'net3.D hello') or q to quit:");
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
