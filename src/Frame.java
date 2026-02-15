import java.io.*;

public class Frame implements Serializable {

    private static String srcMAC;
    private static String dstMAC;
    private final String srcIP;
    private final String dstIP;
    private final String payload;

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

    public Frame(String srcMAC, String dstMAC, String srcIP, String dstIP, String payload) {
        this.srcMAC = srcMAC;
        this.dstMAC = dstMAC;
        this.srcIP = srcIP;
        this.dstIP = dstIP;
        this.payload = payload;
    }

    public String getSrcMAC() {
        return srcMAC;
    }

    public void chgSrcMAC(String sourceMAC){
        this.srcMAC = sourceMAC;
    }

    public String getDstMAC() {
        return dstMAC;
    }

    public void chgDstMAC(String destMAC){
        this.dstMAC = destMAC;
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
                "srcMAC='" + srcMAC + '\'' +
                ", dstMAC='" + dstMAC + '\'' +
                "srcIP='" + srcIP + '\'' +
                "dstIP='" + dstIP + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}