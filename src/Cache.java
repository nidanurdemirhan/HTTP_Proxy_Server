import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class Cache {
    private final int cacheSize;
    private final String cacheDir = "cache";
    private final LinkedList<String> cacheOrder;
    private final Map<String, String> cacheMap;

    public Cache(int cacheSize) {
        this.cacheSize = cacheSize;
        this.cacheOrder = new LinkedList<>();
        this.cacheMap = new HashMap<>();

        // Ensure the cache directory exists
        new File(cacheDir).mkdir();
        clearCacheFolder(new File("cache"));
    }

    public boolean checkCache(String absoluteURL) {
        return cacheMap.containsKey(absoluteURL);
    }

    public void addToCache(String absoluteURL, byte[] data) {
        try {
            String cacheFileName = generateCacheKey(absoluteURL);
            Path cacheFilePath = Paths.get(cacheDir, cacheFileName);

            // Save data to file
            Files.write(cacheFilePath, data);

            // Update cache metadata
            cacheMap.put(absoluteURL, cacheFilePath.toString());
            cacheOrder.add(absoluteURL);

            // Evict oldest entry if cache is full
            if (cacheOrder.size() > cacheSize) {
                String oldestURL = cacheOrder.poll();
                String oldestFilePath = cacheMap.remove(oldestURL);
                if (oldestFilePath != null) {
                    Files.deleteIfExists(Paths.get(oldestFilePath));
                }
            }

            System.out.println("Added to cache: " + absoluteURL);
        } catch (IOException e) {
            System.err.println("Error adding to cache: " + e.getMessage());
        }
    }

    public byte[] getFromCache(String absoluteURL) {
        try {
            String cacheFilePath = cacheMap.get(absoluteURL);
            if (cacheFilePath != null) {
                return Files.readAllBytes(Paths.get(cacheFilePath));
            }
        } catch (IOException e) {
            System.err.println("Error retrieving from cache: " + e.getMessage());
        }
        return null;
    }

    private String generateCacheKey(String uri) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(uri.getBytes());
            StringBuilder key = new StringBuilder();
            for (byte b : hash) {
                key.append(String.format("%02x", b));
            }
            return key.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating cache key", e);
        }
    }


    private static void clearCacheFolder(File folder) {
        if (folder.exists() && folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                file.delete(); // Delete each file
            }
        }
    }

    public void updateEntry(String absoluteURL, byte[] data) {
        try {
            String cacheFileName = generateCacheKey(absoluteURL);
            Path cacheFilePath = Paths.get(cacheDir, cacheFileName);

            // Save data to file
            Files.write(cacheFilePath, data);

            // Change the order at the end of the list
            cacheOrder.remove(absoluteURL);
            cacheOrder.add(absoluteURL);

            // Evict oldest entry if cache is full
            if (cacheOrder.size() > cacheSize) {
                String oldestURL = cacheOrder.poll();
                String oldestFilePath = cacheMap.remove(oldestURL);
                if (oldestFilePath != null) {
                    Files.deleteIfExists(Paths.get(oldestFilePath));
                }
            }

            System.out.println("File is modified, updated cache: " + absoluteURL);
        } catch (IOException e) {
            System.err.println("Error updating cache: " + e.getMessage());
        }
    }


    // Check if the response is modified or not based on the Content-Length header
    public boolean isModified(String absoluteURL) {
        // Get the cached data as a byte array
        byte[] cachedData = getFromCache(absoluteURL);
    
        // Convert the byte[] to a String
        String cachedDataString = new String(cachedData);
    
        // Split the string by line breaks to get each header line
        String[] headerLines = cachedDataString.split("\r\n|\n|\r");
    
        // Iterate through the header lines
        for (String line : headerLines) {
            // Check for Content-Length header
            if (line.startsWith("Content-Length:")) {
                int contentLength = Integer.parseInt(line.substring(16).trim());
                System.out.println("Content-Length: " + contentLength);
                return contentLength % 2 == 0;
            }
        }
    
        // If Content-Length header is not found, assume the response is modified
        System.out.println("Warning: Content-Length header not found");
        return true;
    }
    


   
    
}
