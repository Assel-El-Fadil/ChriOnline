package Client.util;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

public class AnimationUtils {

    /**
     * Applies a continuous pulsing animation to a node on hover.
     */
    public static void makePulsingOnHover(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(800), node);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.1);
        st.setToY(1.1);
        st.setAutoReverse(true);
        st.setCycleCount(ScaleTransition.INDEFINITE);

        node.setOnMouseEntered(e -> st.playFromStart());
        node.setOnMouseExited(e -> {
            st.stop();
            node.setScaleX(1.0);
            node.setScaleY(1.0);
        });
    }

    /**
     * Applies a smooth pop-in animation when the node is first displayed.
     */
    public static void popIn(Node node, int delayMillis) {
        node.setOpacity(0);
        node.setTranslateY(20);

        FadeTransition ft = new FadeTransition(Duration.millis(600), node);
        ft.setToValue(1.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(600), node);
        tt.setToY(0);

        ParallelTransition pt = new ParallelTransition(node, ft, tt);
        pt.setDelay(Duration.millis(delayMillis));
        pt.play();
    }

    /**
     * Applies a 3D-like parallax effect to an image/node based on mouse movement.
     */
    public static void makeMouseParallax(Node node) {
        node.getParent().setOnMouseMoved(e -> {
            double mouseX = e.getX();
            double mouseY = e.getY();
            double width = node.getParent().getBoundsInLocal().getWidth();
            double height = node.getParent().getBoundsInLocal().getHeight();
            
            double deltaX = (mouseX - width / 2) / width;
            double deltaY = (mouseY - height / 2) / height;

            TranslateTransition tt = new TranslateTransition(Duration.millis(150), node);
            tt.setToX(-deltaX * 20); // shift opposite to mouse
            tt.setToY(-deltaY * 20);
            tt.play();
        });
        
        node.getParent().setOnMouseExited(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(300), node);
            tt.setToX(0);
            tt.setToY(0);
            tt.play();
        });
    }
}
