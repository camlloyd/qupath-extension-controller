package qupath.ext.controller.ui;

import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.net.URL;

class UiUtils {
    static void loadFXML(Object controller, URL fxml) throws IOException {
        var loader = new FXMLLoader(fxml);
        loader.setRoot(controller);
        loader.setController(controller);
        loader.load();
    }
}
