import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RespParser {

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                if (next == '\n') {
                    break;
                }
                sb.append((char) b);
                if (next != -1) sb.append((char) next);
            } else {
                sb.append((char) b);
            }
        }
        if (b == -1 && sb.isEmpty()) {
            return null;
        }
        return sb.toString();
    }

    public static List<String> parseCommand(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null) {
            return null;
        }

        if (!line.startsWith("*")) {
            return List.of(line.trim().split("\\s+"));
        }

        int numElements = Integer.parseInt(line.substring(1).trim());
        List<String> commandArgs = new ArrayList<>(numElements);

        for (int i = 0; i < numElements; i++) {
            String lengthLine = readLine(in);
            if (lengthLine == null || !lengthLine.startsWith("$")) {
                throw new IOException("Protocol Error: Expected bulk string indicator '$'");
            }

            int length = Integer.parseInt(lengthLine.substring(1).trim());
            byte[] bytes = new byte[length];
            int totalBytesRead = 0;

            while (totalBytesRead < length) {
                int read = in.read(bytes, totalBytesRead, length - totalBytesRead);
                if (read == -1) {
                    throw new IOException("Protocol Error: Unexpected end of stream while reading bulk string");
                }
                totalBytesRead += read;
            }

            in.read(); // \r
            in.read(); // \n

            commandArgs.add(new String(bytes, StandardCharsets.UTF_8));
        }

        return commandArgs;
    }

    public static byte[] toSimpleString(String s) {
        return ("+" + s + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] toError(String message) {
        return ("-ERR " + message + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] toInteger(long value) {
        return (":" + value + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] toBulkString(String s) {
        if (s == null) {
            return "$-1\r\n".getBytes(StandardCharsets.US_ASCII);
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[header.length + bytes.length + 2];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(bytes, 0, result, header.length, bytes.length);
        result[result.length - 2] = '\r';
        result[result.length - 1] = '\n';
        return result;
    }

    public static byte[] toArray(List<byte[]> serializedElements) {
        if (serializedElements == null) {
            return "*-1\r\n".getBytes(StandardCharsets.US_ASCII);
        }
        StringBuilder header = new StringBuilder("*" + serializedElements.size() + "\r\n");
        byte[] headerBytes = header.toString().getBytes(StandardCharsets.US_ASCII);

        int totalLen = headerBytes.length;
        for (byte[] el : serializedElements) {
            totalLen += el.length;
        }

        byte[] result = new byte[totalLen];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);

        int offset = headerBytes.length;
        for (byte[] el : serializedElements) {
            System.arraycopy(el, 0, result, offset, el.length);
            offset += el.length;
        }
        return result;
    }
}