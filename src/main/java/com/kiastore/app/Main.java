package com.kiastore.app;

import com.kiastore.db.DatabaseBootstrap;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class Main extends Application {

    private static Stage primary;

    public static Stage primaryStage() { return primary; }

    @Override
    public void start(Stage stage) throws IOException, java.sql.SQLException {
        primary = stage;
        
        // Bootstrap database schema and default users
        try {
            DatabaseBootstrap.run();
        } catch (Exception e) {
            System.err.println("[Main] Database bootstrap failed: " + e.getMessage());
            e.printStackTrace();
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());

        stage.setTitle("KIA Store ERP - Login");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
    }

    private void shutdown() {
        try { com.kiastore.db.ConnectionFactory.shutdown(); } catch (Exception ignore) {}
    }

    public static void main(String[] args) {
        System.setProperty("prism.lcdtext", "false");
        launch(args);
    }

    /**
     * Helper to load and switch scenes.
     */
    public static void setRoot(String fxml, String title) throws IOException {
        Parent root = FXMLLoader.load(Objects.requireNonNull(Main.class.getResource("/fxml/" + fxml)));
        primary.getScene().setRoot(root);
        primary.setTitle(title);
    }
}
