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
        try (BufferedReader inputRequest = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter outputMessage = new PrintWriter(socket.getOutputStream(), true)) {
            String requestLine = inputRequest.readLine(); //Read the request message
            if (requestLine == null || requestLine.isEmpty()) {
                sendBadRequest(outputMessage);
                return;
            }

            System.out.println("Request: " + requestLine);

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                sendBadRequest(outputMessage);
                return;
            }

            String method = requestParts[0];
            String uri = requestParts[1];

            if (!method.equals("GET")) {
                sendNotImplemented(outputMessage);
                return;
            }

            // Validate the URI and extract the requested size
            int requestedSize;
            try {
                String sizeString = uri.substring(1); // Remove leading slash
                requestedSize = Integer.parseInt(sizeString);

                if (requestedSize < 100 || requestedSize > 20000) {
                    sendBadRequest(outputMessage);
                    return;
                }
            } catch (NumberFormatException e) {
                sendBadRequest(outputMessage);
                return;
            }

            // Generate and send the HTML document
            String htmlContent = generateHtmlDocument(requestedSize);
            sendResponse(outputMessage, htmlContent);

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
    private void sendResponse(PrintWriter outputResponse, String documentHTML) {
        outputResponse.println("HTTP/1.1 200 OK");
        outputResponse.println("Content-Type: text/html");
        outputResponse.println("Content-Length: " + documentHTML.length());
        outputResponse.println(); // Blank line separates headers from the body
        outputResponse.print(documentHTML);
        outputResponse.flush();
    }

    // Sends a 400 Bad Request response
    private void sendBadRequest(PrintWriter outputResponse) {
        outputResponse.println("HTTP/1.1 400 Bad Request");
        outputResponse.println("Content-Type: text/plain");
        outputResponse.println("Content-Length: 11");
        outputResponse.println();
        outputResponse.print("Bad Request");
        outputResponse.flush();
    }

    // Sends a 501 Not Implemented response
    private void sendNotImplemented(PrintWriter outputResponse) {
        outputResponse.println("HTTP/1.1 501 Not Implemented");
        outputResponse.println("Content-Type: text/plain");
        outputResponse.println("Content-Length: 15");
        outputResponse.println();
        outputResponse.print("Not Implemented");
        outputResponse.flush();
    }
}