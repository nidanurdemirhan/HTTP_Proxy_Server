import java.io.*;
import java.net.*;
import java.util.Scanner;

public class HttpServer {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Port: ");
        int port = Integer.parseInt(sc.nextLine());

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                ClientHandler clientHandler = new ClientHandler(socket);
                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
