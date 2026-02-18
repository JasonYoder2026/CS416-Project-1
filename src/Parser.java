import java.io.*;
import java.util.*;

public class Parser {

    public static class DeviceInfo {
        public final String id;
        public final String ip;
        public final String gateway;
        public final int port;
        public final List<String> vips;

        public DeviceInfo(String id, String ip, String gateway, int port, List<String> vips) {
            this.id = id;
            this.ip = ip;
            this.gateway = gateway;
            this.port = port;
            this.vips = vips;
        }
    }

    private final Map<String, DeviceInfo> devices = new HashMap<>();
    private final Map<String, List<String>> links = new HashMap<>();
    private final Map<String, List<String>> vipMap = new HashMap<>();
    private final Map<String, String> subnetGateways = new HashMap<>();

    public Parser(String filename) throws IOException {
        parseConfig(filename);
        assignGateways();
    }

    private void parseConfig(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\s+");

            if (parts[0].equalsIgnoreCase("DEVICE")) {
                String id = parts[1];
                String ip = parts[2];
                int port = Integer.parseInt(parts[3]);
                String vipField = parts[4];
                List<String> vips = Arrays.asList(vipField.split("-"));

                devices.put(id, new DeviceInfo(id, ip, null, port, vips));
                vipMap.put(id, vips);
                links.putIfAbsent(id, new ArrayList<>());
            }
            else if (parts[0].equalsIgnoreCase("LINK")) {
                String a = normalizeRouter(parts[1]);
                String b = normalizeRouter(parts[2]);

                links.putIfAbsent(a, new ArrayList<>());
                links.putIfAbsent(b, new ArrayList<>());

                links.get(a).add(b);
                links.get(b).add(a);

                for (Map.Entry<String, List<String>> entry : links.entrySet()) {
                    System.out.println(entry.getKey() + " = " + entry.getValue());
                }
            }
            else if (parts[0].equalsIgnoreCase("GATEWAY")) {
                String subnet = parts[1];
                String routerInterface = parts[2];
                subnetGateways.put(subnet, routerInterface);
            }
        }

        br.close();
    }

    private String normalizeRouter(String id) {
        if (id.endsWith("L") || id.endsWith("R")) {
            String base = id.substring(0, id.length() - 1);
            if (base.equals("R1") || base.equals("R2")) {
                return base;
            }
        }
        return id;
    }

    private void assignGateways() {
        for (DeviceInfo d : devices.values()) {
            String firstVip = d.vips.get(0);
            String subnet = firstVip.split("\\.")[0];

            String gatewayVip = subnetGateways.get(subnet);

            DeviceInfo updated = new DeviceInfo(
                    d.id,
                    d.ip,
                    gatewayVip,
                    d.port,
                    d.vips
            );

            devices.put(d.id, updated);
        }
    }

    public DeviceInfo getDevice(String id) {
        return devices.get(id);
    }

    public List<String> getVip(String id) {
        DeviceInfo info = getDevice(id);
        if (info == null) {
            System.out.println("No device found for: " + id);
            return Collections.emptyList();
        }

        System.out.println("VIPs for " + id + ": " + info.vips);
        return info.vips;
    }

    public String getGateway(String id) {
        DeviceInfo info = getDevice(id);
        if (info == null) {
            System.out.println("No device found for: " + id);
            return null;
        }

        System.out.println("Gateway for " + id + ": " + info.gateway);
        return info.gateway;
    }

    public Map<String, List<String>> getAllVips() {
        return vipMap;
    }

    public List<String> getConnections(String id) {
        return links.getOrDefault(id, new ArrayList<>());
    }

    public Set<String> getAllDeviceIDs() {
        return devices.keySet();
    }
}