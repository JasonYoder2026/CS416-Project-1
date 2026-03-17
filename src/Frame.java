import java.io.*;

public class Frame implements Serializable {

    public static final byte TYPE_DATA = 0;
    public static final byte TYPE_ROUTING = 1;

    private final byte type;
    private String srcMAC;
    private String dstMAC;
    private final String srcIP;
    private final String dstIP;
    private final String payload;

    public static Frame createRoutingUpdate(String srcMAC, String dstMAC, String dvPayload) {
        return new Frame(TYPE_ROUTING, srcMAC, dstMAC, "", "", dvPayload);
    }

    public Frame(String srcMAC, String dstMAC, String srcIP,  String dstIP, String payload) {
        this(TYPE_DATA, srcMAC, dstMAC, srcIP, dstIP, payload);
    }

    public Frame(byte type, String srcMAC, String dstMAC, String srcIP, String dstIP, String payload) {
        this.type = type;
        this.srcMAC = srcMAC;
        this.dstMAC = dstMAC;
        this.srcIP = srcIP;
        this.dstIP = dstIP;
        this.payload = payload;
    }

    public boolean isRoutingUpdate() { return type == TYPE_ROUTING; }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(this);
        out.flush();
        return bos.toByteArray();
    }

    public static Frame fromBytes(byte[] bytes) throws IOException {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream in = new ObjectInputStream(bis);
            return (Frame) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    public String getSrcMAC() {
        return srcMAC;
    }

    public void chgSrcMAC(String sourceMAC){
        srcMAC = sourceMAC;
    }

    public String getDstMAC() {
        return dstMAC;
    }

    public void chgDstMAC(String destMAC){
        dstMAC = destMAC;
    }

    public String getSrcIP() {
        return srcIP;
    }

    public String getDstIP() {
        return dstIP;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Frame{" +
                "type=" + (type == TYPE_ROUTING ? "ROUTING " : "DATA ") +
                "srcMAC='" + srcMAC + '\'' +
                ", dstMAC='" + dstMAC + '\'' +
                "srcIP='" + srcIP + '\'' +
                "dstIP='" + dstIP + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}