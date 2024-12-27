import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {
    public static void main(String[] args) {
        int port = 8080;
        if (args.length == 1) { // Accept port number as argument
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) { // If an invalid argument is entered, start server with port 8080 (HTTP)
                System.out.println("Invalid port number. Default port 8080 will be used.");
            }
        } else { // If no argument is entered, start server with port 8080 (HTTP)
            System.out.println("No argument entered. Default port 8080 will be used.");
        }
        System.out.println("Server is running with port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept(); // Accept Client Socket
                Thread thread = new Thread(new Session(socket)); // Handle the connection in a new thread
                thread.start();
            }
        } catch (IOException e) { // IO Exception for Socket
            System.out.println("Error: " + e.getMessage());
        }
    }
}
