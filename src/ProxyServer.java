
import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.*;

public class ProxyServer {
    private static final int PROXY_PORT = 8888;
    private static final int MAX_URI_SIZE = 9999;
    //private static final int CACHE_SIZE = 5; //delete this later, get from user
    private static final int SCOKET_TIMEOUT = 10000; // 10 seconds

    //private static final Cache cache = new Cache(CACHE_SIZE);
    private static Cache cache;

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(10);


        

        // Prompt user for cache size
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter cache size: ");
        int cacheSize = scanner.nextInt();
        scanner.close();

        // Create cache with user-specified size
        cache = new Cache(cacheSize);

        

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
            
            
             
            // -------------------------------START OF CHECKING FORMAT

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
            // -----------------------------END OF FORMAT CHECKING 


            // Read and store all headers
            StringBuilder headers = new StringBuilder();
            String headerLine;
            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();

            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
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
                        clientOutput.write(cachedData);
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
                webServerSocket.setSoTimeout(SCOKET_TIMEOUT);
    
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