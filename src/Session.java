import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Session implements Runnable {
    private final Socket socket;  // A new session with the Client socket

    public Session(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() { // Input request is the request came from the client, output message will be the response of the server
        try (BufferedReader inputRequest = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter outputMessage = new PrintWriter(socket.getOutputStream(), true)) {

            String strInputRequest = inputRequest.readLine(); // Read the request as String
            String[] strInputRequestParts = strInputRequest.split(" "); // Split the request into parts

            int documentSize;
            try {
                documentSize = Integer.parseInt(strInputRequestParts[1].substring(1)); // Retrieve the requested document size
                if (!strInputRequestParts[0].equals("GET")) { // If the HTTP method is any other than GET method, not implemented
                    sendResponse(outputMessage, "501 Not Implemented", "text/plain", "Not Implemented");
                    return;
                } else if (documentSize < 100 || documentSize > 20000) { // If the size of the requested HTML doc is invalid, bad request
                    sendResponse(outputMessage, "400 Bad Request", "text/plain", "Bad Request");
                    return;
                }
            } catch (NumberFormatException e) {  // If the second part of the request cannot be converted into an int, invalid request
                sendResponse(outputMessage, "400 Bad Request", "text/plain", "Bad Request");
                return;
            }
            // If the code still hasn't returned, then request is valid. Send the requested HTML document
            sendResponse(outputMessage, "200 OK", "text/html", returnHTML(documentSize));
        } catch (IOException e) { // Connection error between server and client occurred
            System.out.println("Connection error.");
        } finally {
            try {
                socket.close(); // After the session is completed, close the socket
            } catch (IOException e) { // The socket cannot be closed
                System.out.println("Socket cannot be closed.");
            }
        }
    }

    private String returnHTML(int size) {
        String html = "<HTML><HEAD><TITLE>HTML Document</TITLE></HEAD><BODY>"; // Header of the HTML document, size = 53
        int contentSize = size - 67; // Calculate the size of the repeated a's
        String content = ""; // Content of the HTML document
        for (int i = 0; i < contentSize; i++) {
            content += 'a'; // Repeatedly add a's for desired amount
        }
        html += content + "</BODY></HTML>"; // End of the HTML document, size = 14
        return html;
    }

    private void sendResponse(PrintWriter outputMessage, String httpResponse, String contentType, String responseContent) {
        outputMessage.println( "HTTP/1.0 " + httpResponse + "\n" + // HTTP response
                                "Content-Type: " + contentType + "\n" +  // Content Type
                                "Content-Length: " + responseContent.length() + "\n\n" +  // Content Length
                                responseContent); // HTML content
        outputMessage.flush(); // Flush the output response, so the HTML document can be seen on the browser
    }
}