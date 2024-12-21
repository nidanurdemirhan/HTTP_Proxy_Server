
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ProxyServer {
    private static final int PROXY_PORT = 8888;
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

            // Read the request line
            String requestLine = reader.readLine();

            //We do not support https requests and they start with CONNECT, we only accept starting with GET
            if (requestLine == null || !requestLine.startsWith("GET")) {
                sendError(clientOutput, 400, "Bad Request");
                return;
            }


            System.out.println("Client request: " + requestLine);
            
            
             
    
            // Parse the request URI
            String[] parts = requestLine.split(" ");

            if (parts.length < 2) {
                sendError(clientOutput, 400, "Bad Request");
                System.out.println("Error: " + parts.length);
                return;
            }
    
            
            String absoluteURL = parts[1];
            String host = null;
            String relativeURL = "";
            int port = 0;


            // Check if the request URI is an absolute URI
            if (absoluteURL.startsWith("http://")) {
                try {
                    // Extract host and port from the absolute URI
                    URL urlObject = new URL(parts[1]);
                    host = urlObject.getHost();
                    port = (urlObject.getPort() != -1) ? urlObject.getPort() : 80; // Default port 80 if not specified
                    relativeURL = urlObject.getPath() + (urlObject.getQuery() != null ? "?" + urlObject.getQuery() : ""); // Convert to relative path
                } catch (MalformedURLException e) {
                    sendError(clientOutput, 400, "Bad Request");
                    System.out.println("Error parsing absolute URI: " + absoluteURL);
                    return;
                }
            }
    
            //System.out.println("host: " + host + ", port: " + port + ", relative: " + relativeURL);
            

            // Check requested file size for incoming request to localhost:8080
            if(host.equals("localhost") && port == 8080){
                String fileSizeString = relativeURL.substring(1); // Remove leading slash

                int fileSize = 0;
                try {
                    fileSize = Integer.parseInt(fileSizeString);
                } catch (NumberFormatException e) {
                    sendError(clientOutput, 400, "Bad Request");
                    System.out.println("Error parsing file size: " + fileSizeString);
                    return;
                }
    
                if (fileSize > MAX_URI_SIZE) {
                    sendError(clientOutput, 414, "Request-URI Too Long");
                    System.out.println("Error file size is greater than 9999: " + fileSize);
                    return;
                }
            }


    
            // Read and store all headers
            StringBuilder headers = new StringBuilder();
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                headers.append(headerLine).append("\r\n");
            }
    
            // Forward the request to the target web server
            try (Socket webServerSocket = new Socket(host, port);
                 InputStream webServerInput = webServerSocket.getInputStream();
                 OutputStream webServerOutput = webServerSocket.getOutputStream()) {
    
                PrintWriter webServerWriter = new PrintWriter(webServerOutput, true);
    
                // Send the relative request line and headers to the web server
                webServerWriter.print("GET " + relativeURL + " HTTP/1.1\r\n"); // Use \r\n
                webServerWriter.print(headers.toString()); // Forward all headers
                webServerWriter.println(); // Blank line to end the header section
                webServerWriter.flush();
    
                // Relay the response back to the client
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = webServerInput.read(buffer)) != -1) {
                    clientOutput.write(buffer, 0, bytesRead);
                }

            } catch (IOException e) {
                sendError(clientOutput, 404, "Not Found");
                System.err.println("Error connecting to the web server: " + e.getMessage());
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