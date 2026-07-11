package com.kiastore.ui.controller;

import com.kiastore.model.Part;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.List;

/**
 * Custom sliding toast notification popup that appears in the bottom-right corner of the screen.
 * Displays a list of low-stock parts. Clicking it triggers a callback (e.g., navigating to filtered Parts).
 */
public class NotificationPopup {

    private final Stage stage;
    private final double width = 360;
    private final double height = 220;

    public NotificationPopup(List<Part> lowStockParts, Runnable onClickAction) {
        stage = new Stage(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.initOwner(null); // Keep it free floating

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle(
            "-fx-background-color: #FFFFFF;" +
            "-fx-border-color: #EF4444;" +
            "-fx-border-width: 0 4 0 0;" + // Thick red border on right in RTL
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 15, 0, 0, 5);"
        );
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🚨");
        icon.setStyle("-fx-font-size: 16;");
        Label title = new Label("تنبيه انخفاض المخزون");
        title.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #EF4444;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label closeBtn = new Label("✕");
        closeBtn.setStyle("-fx-font-size: 14; -fx-text-fill: #707070; -fx-cursor: hand;");
        closeBtn.setOnMouseClicked(e -> {
            e.consume();
            dismiss();
        });
        header.getChildren().addAll(icon, title, spacer, closeBtn);
        root.getChildren().add(header);

        // Subtitle
        Label subtitle = new Label("الأصناف التالية اقتربت من النفاد (اضغط للتفاصيل):");
        subtitle.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 11; -fx-text-fill: #707070;");
        root.getChildren().add(subtitle);

        // Items list in ScrollPane
        VBox itemsBox = new VBox(6);
        itemsBox.setStyle("-fx-background-color: transparent;");

        for (Part p : lowStockParts) {
            HBox itemRow = new HBox(8);
            itemRow.setAlignment(Pos.CENTER_LEFT);
            Label dot = new Label("•");
            dot.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12;");
            
            Label name = new Label(p.getFullName());
            name.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 11; -fx-text-fill: #363636;");
            name.setWrapText(true);
            HBox.setHgrow(name, Priority.ALWAYS);

            Label stockInfo = new Label(p.getCurrentStock() + " / " + p.getMinStock());
            stockInfo.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: #707070;");

            itemRow.getChildren().addAll(dot, name, stockInfo);
            itemsBox.getChildren().add(itemRow);
        }

        ScrollPane scroll = new ScrollPane(itemsBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(110);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        // Clicking root runs action
        root.setOnMouseClicked(e -> {
            dismiss();
            if (onClickAction != null) {
                Platform.runLater(onClickAction);
            }
        });

        Scene scene = new Scene(root, width, height);
        scene.setFill(null); // transparent window corners
        stage.setScene(scene);

        // Position bottom-right of screen
        javafx.geometry.Rectangle2D visualBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        stage.setX(visualBounds.getMaxX() - width - 20);
        stage.setY(visualBounds.getMaxY() - height - 20);

        // Slide in animation
        root.setTranslateY(50);
        root.setOpacity(0);
        stage.show();

        FadeTransition ft = new FadeTransition(Duration.millis(300), root);
        ft.setToValue(1.0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), root);
        tt.setByY(-50); // slide upward from bottom
        
        ft.play();
        tt.play();

        // Auto dismiss after 15 seconds
        new Thread(() -> {
            try {
                Thread.sleep(15000);
                Platform.runLater(this::dismiss);
            } catch (InterruptedException ignored) {}
        }).start();
    }

    private void dismiss() {
        if (stage.isShowing()) {
            FadeTransition ft = new FadeTransition(Duration.millis(300), stage.getScene().getRoot());
            ft.setToValue(0.0);
            ft.setOnFinished(e -> stage.close());
            ft.play();
        }
    }
}
