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

            in.read();
            in.read();

            commandArgs.add(new String(bytes, StandardCharsets.UTF_8));
        }

        return commandArgs;
    }
}