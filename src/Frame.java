import java.io.*;

public class Frame implements Serializable {

    private final String srcMAC;
    private final String dstMAC;
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

    public Frame(String srcMAC, String dstMAC, String payload) {
        this.srcMAC = srcMAC;
        this.dstMAC = dstMAC;
        this.payload = payload;
    }

    public String getSrcMAC() {
        return srcMAC;
    }

    public String getDstMAC() {
        return dstMAC;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Frame{" +
                "srcMAC='" + srcMAC + '\'' +
                ", dstMAC='" + dstMAC + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}