import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RedisServer {
    private static final int PORT = 6379;

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
}