import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RedisServer {
    private static final int PORT = 6379;

    public static void main(String[] args) {
        System.out.println("Starting Redis Server on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // SO_REUSEADDR allows immediate restart without "Address already in use" errors
            serverSocket.setReuseAddress(true);
            System.out.println("Server listening and ready for connections on port " + PORT);

            while (true) {
                // Blocks until a new client connects
                Socket clientSocket = serverSocket.accept();
                System.out.println("⚡ New client connected from: " + clientSocket.getRemoteSocketAddress());

                // Spawn a lightweight virtual thread for this connection (Java 21+)
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

            byte[] buffer = new byte[1024];
            int bytesRead;

            // Continuously read bytes sent by the client
            while ((bytesRead = in.read(buffer)) != -1) {
                String rawInput = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                System.out.println("Raw bytes received from client:\n" + rawInput.replace("\r", "\\r").replace("\n", "\\n\n"));

                // Reply with valid RESP Simple String (+PONG\r\n)
                byte[] response = "+PONG\r\n".getBytes(StandardCharsets.UTF_8);
                out.write(response);
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + socket.getRemoteSocketAddress());
        }
    }
}