import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Aof {
    private static final String AOF_FILE = "appendonly.aof";
    private static final Set<String> WRITE_COMMANDS = Set.of(
        "SET", "INCR", "DECR", "INCRBY", "DECRBY", "MSET",
        "HSET", "HDEL",
        "LPUSH", "RPUSH", "LPOP", "RPOP",
        "SADD", "SREM",
        "DEL", "EXPIRE", "PEXPIRE"
    );

    private final FileOutputStream fos;

    public Aof() throws IOException {
        this.fos = new FileOutputStream(AOF_FILE, true);
    }

    public static List<List<String>> readCommands() throws IOException {
        File file = new File(AOF_FILE);
        if (!file.exists() || file.length() == 0) {
            return List.of();
        }

        List<List<String>> commands = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            while (true) {
                List<String> cmd = RespParser.parseCommand(fis);
                if (cmd == null || cmd.isEmpty()) {
                    break;
                }
                commands.add(cmd);
            }
        }
        return commands;
    }

    public synchronized void writeCommand(List<String> commandArgs) {
        if (commandArgs == null || commandArgs.isEmpty()) {
            return;
        }

        String cmd = commandArgs.get(0).toUpperCase();
        if (!WRITE_COMMANDS.contains(cmd)) {
            return;
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("*").append(commandArgs.size()).append("\r\n");
            for (String arg : commandArgs) {
                byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
                sb.append("$").append(bytes.length).append("\r\n");
                sb.append(arg).append("\r\n");
            }

            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException e) {
            System.err.println("AOF Write Error: " + e.getMessage());
        }
    }

    public synchronized void close() {
        try {
            if (fos != null) {
                fos.close();
            }
        } catch (IOException ignored) {
        }
    }
}