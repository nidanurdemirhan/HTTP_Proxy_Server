import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class Cache {
    private final int cacheSize; // Cache size stated from the input
    private final String cacheDirectory = "cache"; // Directory for cached documents and URLS
    private final LinkedList<String> cacheListOrder = new LinkedList<>();
    private final Map<String, String> urlDocMapping = new HashMap<>(); // Have a "absoluteURL":"HTML document" mapping

    public Cache (int cacheSize) {
        this.cacheSize = cacheSize;
        new File(cacheDirectory).mkdir(); // Create a directory for cached documents
        clearCacheFolder(new File("cache")); // For cases such as program restarts, clear the cache directory
    }

    public boolean isUrlExistsInCache(String absoluteURL) { // Check if the absolute URL exists in the cache
        return urlDocMapping.containsKey(absoluteURL);
    }

    public void addHtmlDocToCache(String absoluteURL, byte[] data) { // Add missed file to cache
        try {
            String cacheFileName = generateCacheKey(absoluteURL); // Key of the cache is the absolute URL
            Path cacheFilePath = Paths.get(cacheDirectory, cacheFileName); // Path of the file is cache + file name
            Files.write(cacheFilePath, data); // Write the given data on the file
            urlDocMapping.put(absoluteURL, cacheFilePath.toString()); // Add the file to the URL-File mapping
            cacheListOrder.add(absoluteURL); // Add the newly cached file to the list from the end
            if (cacheListOrder.size() > cacheSize) { // If the size of the cache exceeds the desired size...
                String oldestURL = cacheListOrder.poll(); // Determine the oldest file (FIFO)
                String oldestFilePath = urlDocMapping.remove(oldestURL); // Remove that file from the mapping
                if (oldestFilePath != null) { // If that file is not null, delete that file
                    Files.deleteIfExists(Paths.get(oldestFilePath));
                }
            }
            System.out.println("Added to cache: " + absoluteURL);
        } catch (IOException e) {
            System.out.println("URL and HTML document cannot be cached");
        }
    }

    public byte[] getHtmlDocFromCache(String absoluteURL) { // Retrieve a document from cache
        try {
            String cacheFilePath = urlDocMapping.get(absoluteURL); // Get the file name with URL mapping
            if (cacheFilePath != null) { // Check if file is not null
                return Files.readAllBytes(Paths.get(cacheFilePath)); // If so, return the content of the file
            }
        } catch (IOException e) {
            System.out.println("Error retrieving the HTML document from cache");
        }
        return null;
    }

    private String generateCacheKey(String uri) { // Generating the cache key for file naming
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5"); // Message digest is used for creating hash values
            byte[] hash = digest.digest(uri.getBytes()); // Compute the hash value for the given URL
            StringBuilder key = new StringBuilder(); // Initialize a string
            for (byte b : hash) { // Construct the string with the computed hash value
                key.append(String.format("%02x", b));
            }
            return key.toString(); // Return the result String
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating cache key");
        }
    }

    private static void clearCacheFolder(File folder) { // Clearing the cache folder for reruns
        if (folder.exists() && folder.isDirectory()) { // If folder exists and the given path is a directory...
            for (File file : folder.listFiles()) { // For each file under the folder...
                file.delete(); // Delete that file
            }
        }
    }

    public void updateCacheFile(String absoluteURL, byte[] data) { // Update the modified cache file
        try {
            String cacheFileName = generateCacheKey(absoluteURL); // Retrieve the cache file name with URL mapping
            Path cacheFilePath = Paths.get(cacheDirectory, cacheFileName); // Find the cache file
            Files.write(cacheFilePath, data); // Write the data given inside that file
            cacheListOrder.remove(absoluteURL); // Remove the file from the list
            cacheListOrder.add(absoluteURL); // Re-add the file, so the file's entry is updated
            System.out.println("File is modified, updated cache: " + absoluteURL);
        } catch (IOException e) {
            System.err.println("Error updating cache: " + e.getMessage());
        }
    }

    public boolean isCacheFileModified(String absoluteURL) { // Check if the file is modified
        byte[] cachedData = getHtmlDocFromCache(absoluteURL); // Retrieve the file content with the given URL
        String cachedDataString = new String(cachedData); // Convert the byte array into String format
        String[] headerLines = cachedDataString.split("\r\n|\n|\r");
        for (String line : headerLines) { // Split the content, and for each line...
            if (line.startsWith("Content-Length:")) { // Check if that line is the Content-Length header
                int contentLength = Integer.parseInt(line.substring(16).trim()); // Retrieve the length
                System.out.println("Content-Length: " + contentLength);
                return contentLength % 2 == 0; // If the length is even, then the file is modified
            }
        }
        System.out.println("Content-Length header not found");
        return true;
    }
}