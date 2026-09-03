import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

                System.out.println("Parsed Command: " + commandArgs);

                byte[] response = "+PONG\r\n".getBytes(StandardCharsets.UTF_8);
                out.write(response);
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + socket.getRemoteSocketAddress());
        }
    }
    private static byte[] dispatchcommand(List<String>args){
        String command = args.get(0).toUpperCase();

        switch(command){
            case "PING":
                if(args.size() == 1){
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
                String value = engine.get(args.get(1));
                return RespParser.toBulkString(value);

            default:
                return RespParser.toError("unknown command '" + command + "'");
        }
    }
}