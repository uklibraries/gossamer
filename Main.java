package gossamer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(final Stage primaryStage) throws Exception{
        FXMLLoader loader = new FXMLLoader(getClass().getResource("gossamer.fxml"));
        Parent root = loader.load();
        GossamerController controller = loader.getController();
        controller.setStage(primaryStage);

        Scene scene = new Scene(root, 600, 275);

        primaryStage.setTitle("Gossamer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
