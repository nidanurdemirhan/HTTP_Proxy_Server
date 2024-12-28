import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ProxyServer {
    private static Cache cache; // Cache of the Proxy Server

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter cache size: "); // Ask the user for the cache size
        int cacheSize = scanner.nextInt(); // Read the input
        scanner.close();

        cache = new Cache(cacheSize); // Create a cache with the given cache size

        try (ServerSocket serverSocket = new ServerSocket(8888)) { // Start the Proxy Server with port 8888
            System.out.println("Proxy server is running on port 8888");
            while (true) {
                Socket socket = serverSocket.accept(); // Accept the client connections
                new Thread(() -> proxySession(socket)).start(); // Handle the client request in a new thread
            }
        } catch (IOException e) { // IO Exception for Socket
            System.out.println(e.getMessage());
        }
    }

    // Input request is the request came from the client, output response will be the response of the server
    private static void proxySession(Socket socket) {
        try (BufferedReader inputRequest = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream outputResponse = socket.getOutputStream()) {

            String requestLine = inputRequest.readLine();
            System.out.println("Client request: " + requestLine);

            String[] strInputRequestParts = requestLine.split(" ");
            if (strInputRequestParts.length < 2 || !requestLine.startsWith("GET")) {
                sendError(outputResponse, 400, "Bad Request");
                return;
            }

            String absoluteURL = strInputRequestParts[1]; // Full URL of the client request
            String host = ""; // Host to be directed
            String requestedSize = ""; // Requested size of the HTML document
            int port = 0; // Port of the host to be directed

            if (absoluteURL.startsWith("http://")) { // Check if the URL states HTTP protocol
                try {
                    String urlWithoutHTTP = absoluteURL.substring(7); // Eliminate the HTTP protocol part, as it is useless
                    int slashIndex = urlWithoutHTTP.indexOf('/'); // Find the index of the backslash
                    // If there's a backslash, assume the rest indicates the file size. Else, no file size stated
                    String hostAndPort = (slashIndex != -1) ? urlWithoutHTTP.substring(0, slashIndex) : urlWithoutHTTP;
                    requestedSize = (slashIndex != -1) ? urlWithoutHTTP.substring(slashIndex) : "";

                    int colonIndex = hostAndPort.indexOf(':'); // Find the index of the :
                    host = hostAndPort.substring(0, colonIndex); // Before : indicates host IP (localhost)
                    port = Integer.parseInt(hostAndPort.substring(colonIndex + 1)); // Rest indicates the port number
                } catch (Exception e) {
                    System.out.println("Invalid URL received: " + absoluteURL); // Sometimes, invalid URLs can be received
                }
            }

            if(host.equals("localhost") && port == 8080) { // Check if the request is in localhost:8080 format
                int htmlFileSize;
                try {
                    htmlFileSize = Integer.parseInt(requestedSize.substring(1)); // Convert the file size into integer
                    if (htmlFileSize > 9999) { // Check whether requested URI is too long
                        sendError(outputResponse, 414, "Request-URI Too Long"); // Send error code 401
                        System.out.println("Error file size is greater than 9999");
                        return;
                    }
                } catch (NumberFormatException e) { // If conversion to integer fails, then it means invalid request
                    sendError(outputResponse, 400, "Bad Request");
                    return;
                }
            }

            // BURADA KALDIM!!!----------------------------------------------------------------------------------------------

            // Read and store all headers
            StringBuilder headers = new StringBuilder();
            String headerLine;
            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();

            while ((headerLine = inputRequest.readLine()) != null && !headerLine.isEmpty()) {
                headers.append(headerLine).append("\r\n");
            }

            // CHECK IF THE CACHE IS VALID (CONDITIONAL GET)
            boolean shouldBeUpdated = false;

             // Serve from cache if available
            if (cache.checkCache(absoluteURL)) {
                // Check if the cached response is still valid
                if (!cache.isModified(absoluteURL)) {
                    System.out.println("Cache hit: " + absoluteURL);
                    byte[] cachedData = cache.getFromCache(absoluteURL);
                    if (cachedData != null) {
                        outputResponse.write(cachedData);
                        return;
                    }
                } else {
                    System.out.println("Cache hit but invalid: " + absoluteURL);
                    shouldBeUpdated = true;
                }
            } else {
                System.out.println("Cache miss: " + absoluteURL);
            }

            // Forward the request to the target web server
            try (Socket webServerSocket = new Socket(host, port);
                 InputStream webServerInput = webServerSocket.getInputStream();
                 OutputStream webServerOutput = webServerSocket.getOutputStream()) {

                // Set socket timeout
                webServerSocket.setSoTimeout(10000);

                PrintWriter webServerWriter = new PrintWriter(webServerOutput, true);

                // Send the relative request line and headers to the web server
                webServerWriter.print("GET " + requestedSize + " HTTP/1.1\r\n"); // Use \r\n
                webServerWriter.print(headers.toString()); // Forward all headers
                webServerWriter.println(); // Blank line to end the header section
                webServerWriter.flush();

                // Relay the response back to the client
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = webServerInput.read(buffer)) != -1) {
                    outputResponse.write(buffer, 0, bytesRead);
                    responseBuffer.write(buffer, 0, bytesRead);
                }
                // If the response is not cached, add it to the cache. If it is cached but invalid, update it
                if (shouldBeUpdated) {
                    cache.updateEntry(absoluteURL, responseBuffer.toByteArray());
                } else {
                    cache.addToCache(absoluteURL, responseBuffer.toByteArray());
                }
            } catch(SocketTimeoutException e){ // Connection close due to timeout and we cache the response (we assume the response is complete)
                // If the response is not cached, add it to the cache. If it is cached but invalid, update it
                if (shouldBeUpdated) {
                    cache.updateEntry(absoluteURL, responseBuffer.toByteArray());
                } else {
                    cache.addToCache(absoluteURL, responseBuffer.toByteArray());
                }
            } catch (IOException e) {
                sendError(outputResponse, 404, "Not Found");
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