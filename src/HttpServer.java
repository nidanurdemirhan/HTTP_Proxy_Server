import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {
    public static void main(String[] args) {
        int port = 8080; //HTTP port is 8080
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("HTTP Server is running."); //Server Socket is opened and is ready for connection
            while (true) {
                Socket socket = serverSocket.accept(); //Accept Client Socket
                System.out.println("A client is connected.");
                Thread thread = new Thread(new ClientHandler(socket)); //Handle the connection in a new thread
                thread.start();
            }
        } catch (IOException e) { //IO Exception for Socket
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
