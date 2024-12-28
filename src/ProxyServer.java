import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
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
            String strInputRequest = inputRequest.readLine();

            if (strInputRequest == null) { //Sometimes request can be null, so return
                return;
            }

            String[] strInputRequestParts = strInputRequest.split(" ");
            System.out.println("Client request: " + strInputRequest);


            if (strInputRequestParts.length < 2 || !strInputRequest.startsWith("GET")) {
                sendErrorResponse(outputResponse, 400, "Bad Request");
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

            if (host.equals("localhost") && port == 8080) { // Check if the request is in localhost:8080 format
                int htmlFileSize;
                try {
                    htmlFileSize = Integer.parseInt(requestedSize.substring(1)); // Convert the file size into integer
                    if (htmlFileSize > 9999) { // Check whether requested URI is too long
                        sendErrorResponse(outputResponse, 414, "Request-URI Too Long"); // Send error code 414
                        System.out.println("Error file size is greater than 9999");
                        return;
                    }
                } catch (NumberFormatException e) { // If conversion to integer fails, then it means invalid request
                    sendErrorResponse(outputResponse, 400, "Bad Request");
                    return;
                }
            }

            boolean shouldBeUpdated = false; // Check if the HTML document associated with the URL should be updated

            // Serve from cache if available
            if (cache.checkCache(absoluteURL)) { // Check if the URL exists in Cache
                if (!cache.isModified(absoluteURL)) { // Check if the URL is modified
                    System.out.println("Cache hit: " + absoluteURL); // Cache is hit. No need to forward anything to HTTP
                    byte[] cachedHtmlDoc = cache.getFromCache(absoluteURL); // Retrieve the document from the Cache
                    if (cachedHtmlDoc != null) { // Check if the document is null. If not null, we can display the document
                        outputResponse.write(cachedHtmlDoc); // Send the HTML document to client
                        return;
                    }
                } else { // Cache is found, however it is modified. Meaning that we should update the cache
                    System.out.println("Cache hit, but modified: " + absoluteURL);
                    shouldBeUpdated = true; //
                }
            } else {
                System.out.println("Cache miss: " + absoluteURL);
            }

            List<String> headers = new ArrayList<>(); // List of all headers received from the client
            String headerLine;
            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();

            // Store all the headers received from the client, so we can forward them to HTTP server
            while ((headerLine = inputRequest.readLine()) != null && !headerLine.isEmpty()) {
                headers.add(headerLine);
            }

            try (Socket proxyHttpSocket = new Socket(host, port); // Connect to HTTP server for miss or modified cache
                 InputStream inputHTTP = proxyHttpSocket.getInputStream(); // Input request to the HTTP server
                 PrintWriter webServerWriter = new PrintWriter(proxyHttpSocket.getOutputStream(), true)) { // Output response from the HTTP server

                proxyHttpSocket.setSoTimeout(10000); // Timeout for 10 seconds

                // All headers received from the client is forwarded to HTTP server with correct format
                String headersString = String.join("\r\n", headers) + "\r\n";

                webServerWriter.print("GET " + requestedSize + " HTTP/1.1\r\n"); // Forward the URL to the HTTP server
                webServerWriter.print(headersString + "\n"); // Forward all headers to the HTTP server
                webServerWriter.flush();

                byte[] buffer = new byte[4096]; // Initialize for file reading
                int bytesRead;
                while ((bytesRead = inputHTTP.read(buffer)) != -1) { // Read the document received from the HTTP server
                    outputResponse.write(buffer, 0, bytesRead); // Forward this HTML document to client
                    responseBuffer.write(buffer, 0, bytesRead);
                }
                if (shouldBeUpdated) { // If the URL received from the client is cache hit but modified, update the cache
                    cache.updateEntry(absoluteURL, responseBuffer.toByteArray());
                } else { // If the URL received from is cache miss, add it to the cache
                    cache.addToCache(absoluteURL, responseBuffer.toByteArray());
                }
            } catch (SocketTimeoutException e) { // Connection close due to timeout, and we cache the response (we assume the response is complete)
                if (shouldBeUpdated) { // If the URL received from the client is cache hit but modified, update the cache
                    cache.updateEntry(absoluteURL, responseBuffer.toByteArray());
                } else { // If the URL received from is cache miss, add it to the cache
                    cache.addToCache(absoluteURL, responseBuffer.toByteArray());
                }
            } catch (IOException e) { // Connection error occurred between Proxy and HTTP, HTTP server may not be running
                sendErrorResponse(outputResponse, 404, "Not Found"); // Send Not Found 404 error to client
                System.out.println("Cannot establish connection with HTTP server");
            }
        } catch (IOException e) { // Connection error occurred between Proxy and Client, Socket Exception
            System.out.println("Connection error with Client Socket");
        }
    }

    private static void sendErrorResponse(OutputStream clientOutput, int statusCode, String message) throws IOException {
        PrintWriter outputWriter = new PrintWriter(clientOutput, true); // Create an error response with Output Writer
        outputWriter.println("HTTP/1.0 " + statusCode + " " + message); // Determine the error code and message
        outputWriter.println("Content-Type: text/plain\n"); // Determine the HTML content type
        outputWriter.println(message); // Add the content, which is the message
        outputWriter.flush(); // Flush the output response, so the HTML document can be seen on the browser
    }
}
