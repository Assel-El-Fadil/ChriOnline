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
        File file = new File(imagePath.trim());
        if (!file.isFile()) {
            return null;
        }
        try {
            return new Image(file.toURI().toString());
        } catch (Exception e) {
            return null;
        }
    }
}
