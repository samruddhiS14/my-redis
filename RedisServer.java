import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class RedisServer {
    private static final int PORT = 6379;
    private static final Engine engine = new Engine();

    public static void main(String[] args) {
        System.out.println("Starting Redis Server on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverSocket.setReuseAddress(true);
            System.out.println("Server listening and ready for connections on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                Thread.ofVirtual().start(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            while (true) {
                List<String> commandArgs = RespParser.parseCommand(in);
                if (commandArgs == null || commandArgs.isEmpty()) {
                    break;
                }

                byte[] response = dispatchCommand(commandArgs);
                out.write(response);
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + socket.getRemoteSocketAddress());
        }
    }

    private static byte[] dispatchCommand(List<String> args) {
        String command = args.get(0).toUpperCase();

        switch (command) {
            case "PING":
                if (args.size() == 1) {
                    return RespParser.toSimpleString("PONG");
                }
                return RespParser.toBulkString(args.get(1));

            case "ECHO":
                if (args.size() != 2) {
                    return RespParser.toError("wrong number of arguments for 'echo' command");
                }
                return RespParser.toBulkString(args.get(1));

            case "SET":
                if (args.size() < 3) {
                    return RespParser.toError("wrong number of arguments for 'set' command");
                }
                engine.set(args.get(1), args.get(2));
                return RespParser.toSimpleString("OK");

            case "GET":
                if (args.size() != 2) {
                    return RespParser.toError("wrong number of arguments for 'get' command");
                }
                return RespParser.toBulkString(engine.get(args.get(1)));

            // --- Day 5 Additions ---

            case "INCR":
                if (args.size() != 2) {
                    return RespParser.toError("wrong number of arguments for 'incr' command");
                }
                try {
                    long newVal = engine.incrBy(args.get(1), 1);
                    return RespParser.toInteger(newVal);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "DECR":
                if (args.size() != 2) {
                    return RespParser.toError("wrong number of arguments for 'decr' command");
                }
                try {
                    long newVal = engine.incrBy(args.get(1), -1);
                    return RespParser.toInteger(newVal);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "INCRBY":
                if (args.size() != 3) {
                    return RespParser.toError("wrong number of arguments for 'incrby' command");
                }
                try {
                    long delta = Long.parseLong(args.get(2));
                    long newVal = engine.incrBy(args.get(1), delta);
                    return RespParser.toInteger(newVal);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "DECRBY":
                if (args.size() != 3) {
                    return RespParser.toError("wrong number of arguments for 'decrby' command");
                }
                try {
                    long delta = Long.parseLong(args.get(2));
                    long newVal = engine.incrBy(args.get(1), -delta);
                    return RespParser.toInteger(newVal);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "STRLEN":
                if (args.size() != 2) {
                    return RespParser.toError("wrong number of arguments for 'strlen' command");
                }
                return RespParser.toInteger(engine.strLen(args.get(1)));

            case "MSET":
                // Must have pairs: MSET key1 val1 key2 val2 -> odd total arguments
                if (args.size() < 3 || (args.size() - 1) % 2 != 0) {
                    return RespParser.toError("wrong number of arguments for 'mset' command");
                }
                List<String> msetKeys = new ArrayList<>();
                List<String> msetVals = new ArrayList<>();
                for (int i = 1; i < args.size(); i += 2) {
                    msetKeys.add(args.get(i));
                    msetVals.add(args.get(i + 1));
                }
                engine.mset(msetKeys, msetVals);
                return RespParser.toSimpleString("OK");

            case "MGET":
                if (args.size() < 2) {
                    return RespParser.toError("wrong number of arguments for 'mget' command");
                }
                List<String> queryKeys = args.subList(1, args.size());
                List<String> results = engine.mget(queryKeys);
                List<byte[]> serializedValues = new ArrayList<>(results.size());
                for (String val : results) {
                    serializedValues.add(RespParser.toBulkString(val));
                }
                return RespParser.toArray(serializedValues);

            default:
                return RespParser.toError("unknown command '" + command + "'");
        }
    }
}