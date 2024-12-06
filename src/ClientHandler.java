import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                sendBadRequest(out);
                return;
            }

            System.out.println("Request: " + requestLine);

            // Parse the request line
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                sendBadRequest(out);
                return;
            }

            String method = requestParts[0];
            String uri = requestParts[1];
            String version = requestParts[2];

            // Validate HTTP method
            if (!method.equals("GET")) {
                sendNotImplemented(out);
                return;
            }

            // Validate the URI and extract the requested size
            int requestedSize;
            try {
                String sizeString = uri.substring(1); // Remove leading slash
                requestedSize = Integer.parseInt(sizeString);

                if (requestedSize < 100 || requestedSize > 20000) {
                    sendBadRequest(out);
                    return;
                }
            } catch (NumberFormatException e) {
                sendBadRequest(out);
                return;
            }

            // Generate and send the HTML document
            String htmlContent = generateHtmlDocument(requestedSize);
            sendResponse(out, htmlContent);

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Failed to close socket: " + e.getMessage());
            }
        }
    }

    // Generates an HTML document of the specified size
    private String generateHtmlDocument(int size) {
        StringBuilder builder = new StringBuilder(size);

        // Add required HTML structure
        builder.append("<HTML><HEAD><TITLE>");
        builder.append("Generated HTML");
        builder.append("</TITLE></HEAD><BODY>");

        // Fill the body with repeated content
        int contentSize = size - builder.length() - 14; // Account for closing tags
        for (int i = 0; i < contentSize; i++) {
            builder.append('a'); // Add 'a' repeatedly to fill the size
        }

        builder.append("</BODY></HTML>");
        return builder.toString();
    }

    // Sends a valid HTTP response with the HTML content
    private void sendResponse(PrintWriter out, String htmlContent) {
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: text/html");
        out.println("Content-Length: " + htmlContent.length());
        out.println(); // Blank line separates headers from the body
        out.print(htmlContent);
        out.flush();
    }

    // Sends a 400 Bad Request response
    private void sendBadRequest(PrintWriter out) {
        out.println("HTTP/1.1 400 Bad Request");
        out.println("Content-Type: text/plain");
        out.println("Content-Length: 11");
        out.println();
        out.print("Bad Request");
        out.flush();
    }

    // Sends a 501 Not Implemented response
    private void sendNotImplemented(PrintWriter out) {
        out.println("HTTP/1.1 501 Not Implemented");
        out.println("Content-Type: text/plain");
        out.println("Content-Length: 15");
        out.println();
        out.print("Not Implemented");
        out.flush();
    }
}