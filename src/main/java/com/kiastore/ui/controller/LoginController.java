package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Main;
import com.kiastore.app.Session;
import com.kiastore.model.User;
import com.kiastore.util.Result;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    private void onLogin() {
        hideError();
        String username = usernameField.getText();
        String password = passwordField.getText();

        Result<User> result = AppContext.get().authService.login(username, password);

        if (result.isFail()) {
            showError(result.error());
            return;
        }

        User u = result.value();
        Session.set(u);
        
        try {
            // Logs system audit log
            AppContext.get().auditLogService.log(
                u.getId(), 
                u.getUsername(), 
                "LOGIN", 
                "users", 
                u.getId(), 
                null, 
                "User logged in successfully"
            );

            // Navigate to MainShell FXML layout
            Main.setRoot("MainShell.fxml", "KIA Store ERP - الرئيسية");
        } catch (Exception e) {
            e.printStackTrace();
            showError("فشل الانتقال إلى الشاشة الرئيسية: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
