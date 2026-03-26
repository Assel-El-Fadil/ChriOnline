package Client.util;

import javafx.scene.image.Image;

import java.io.File;

/**
 * Loads a product image from a local filesystem path (image_path column).
 */
public final class ProductImageHelper {

    private ProductImageHelper() {}

    public static Image loadLocalImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        String path = imagePath.trim();
        // If it starts with images/ but doesn't have a /resources/ prefix, try adding it
        if (path.startsWith("images/")) {
            path = "/resources/" + path;
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }

        try {
            // Attempt 1: Direct resource lookup
            var resource = ProductImageHelper.class.getResource(path);
            if (resource != null) {
                return new Image(resource.toExternalForm());
            }

            // Attempt 1b: If it started with /resources/images/, try /images/ (in case src/resources is a resource root)
            if (path.startsWith("/resources/")) {
                String altPath = path.substring(10); // remove "/resources"
                var altResource = ProductImageHelper.class.getResource(altPath);
                if (altResource != null) {
                    return new Image(altResource.toExternalForm());
                }
            }

            // Attempt 2: Try as stream
            var stream = ProductImageHelper.class.getResourceAsStream(path);
            if (stream != null) {
                return new Image(stream);
            }

            // Attempt 3: Try stripping leading slash if it failed
            if (path.startsWith("/")) {
                var stream2 = ProductImageHelper.class.getResourceAsStream(path.substring(1));
                if (stream2 != null) {
                    return new Image(stream2);
                }
            }

            // Fallback: check if it's already an absolute file: or http: URL
            if (path.contains(":/")) {
                return new Image(path);
            }

            // Final fallback: check local file if it exists
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                return new Image(file.toURI().toString());
            }

            System.err.println("[ProductImageHelper] Could not find resource: " + path);
            return null;
        } catch (Exception e) {
            System.err.println("[ProductImageHelper] Error loading image " + path + ": " + e.getMessage());
            return null;
        }
    }
}
