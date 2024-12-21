import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ProxyServer {
    private static final int PROXY_PORT = 8888;
    private static final String WEB_SERVER_HOST = "localhost";
    private static final int WEB_SERVER_PORT = 8080;
    private static final int MAX_URI_SIZE = 9999;

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket = new ServerSocket(PROXY_PORT)) {
            System.out.println("Proxy server running on port " + PROXY_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Proxy server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (InputStream clientInput = clientSocket.getInputStream();
             OutputStream clientOutput = clientSocket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput));
            PrintWriter writer = new PrintWriter(clientOutput, true);

            // Read the client's request
            String requestLine = reader.readLine();
            if (requestLine == null || !requestLine.startsWith("GET")) {
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            System.out.println("Client request: " + requestLine);

            // Parse the request URI
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            String uri = parts[1];
            if (uri.startsWith("http://")) {
                uri = uri.replaceFirst("http://[^/]+", "");
            }

            if (!uri.startsWith("/")) {
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            // Remove leading slash and parse as integer
            try {
                int requestedSize = Integer.parseInt(uri.substring(1));
                if (requestedSize > MAX_URI_SIZE) {
                    sendError(clientOutput, 414, "Request-URI Too Long");
                    return;
                }
            } catch (NumberFormatException e) {
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            // Forward the request to the web server
            try (Socket webServerSocket = new Socket(WEB_SERVER_HOST, WEB_SERVER_PORT);
                 InputStream webServerInput = webServerSocket.getInputStream();
                 OutputStream webServerOutput = webServerSocket.getOutputStream()) {

                PrintWriter webServerWriter = new PrintWriter(webServerOutput, true);

                // Send modified request to web server
                webServerWriter.println("GET " + uri + " HTTP/1.0");
                webServerWriter.println("Host: " + WEB_SERVER_HOST + ":" + WEB_SERVER_PORT);
                webServerWriter.println();
                webServerWriter.flush();

                // Relay the response back to the client
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = webServerInput.read(buffer)) != -1) {
                    clientOutput.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                sendError(clientOutput, 404, "Not Found");
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private static void sendError(OutputStream clientOutput, int statusCode, String message) throws IOException {
        PrintWriter writer = new PrintWriter(clientOutput, true);
        writer.println("HTTP/1.0 " + statusCode + " " + message);
        writer.println("Content-Type: text/plain");
        writer.println();
        writer.println(message);
        writer.flush();
    }
}
