import java.io.*;
import java.util.*;

public class Parser {

    public static class DeviceInfo {
        public final String id;
        public final String ip;
        public final int port;
        public final List<String> macs;

        public DeviceInfo(String id, String ip, int port, List<String> macs) {
            this.id = id;
            this.ip = ip;
            this.port = port;
            this.macs = macs;
        }
    }

    private final Map<String, DeviceInfo> devices = new HashMap<>();
    private final Map<String, List<String>> links = new HashMap<>();
    private final Map<String, List<String>> macMap = new HashMap<>();

    public Parser(String filename) throws IOException {
        parseConfig(filename);
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

                String macField = parts[4];
                List<String> macs = Arrays.asList(macField.split("-"));
                devices.put(id, new DeviceInfo(id, ip, port, macs));
                macMap.put(id, macs);
                links.putIfAbsent(id, new ArrayList<>());
            }

            else if (parts[0].equalsIgnoreCase("LINK")) {
                String a = parts[1];
                String b = parts[2];

                links.putIfAbsent(a, new ArrayList<>());
                links.putIfAbsent(b, new ArrayList<>());

                links.get(a).add(b);
                links.get(b).add(a);
            }
        }

        br.close();
    }

    public DeviceInfo getDevice(String id) {
        return devices.get(id);
    }

    public List<String> getMac(String id) {
        DeviceInfo info = getDevice(id);
        if (info == null) {
            System.out.println("No device found for: " + id);
            return Collections.emptyList();
        }

        System.out.println("MACs for " + id + ": " + info.macs);
        return info.macs;
    }

    public Map<String, List<String>> getAllMacs() {
        return macMap;
    }

    public List<String> getConnections(String id) {
        return links.getOrDefault(id, new ArrayList<>());
    }

    public Set<String> getAllDeviceIDs() {
        return devices.keySet();
    }
}