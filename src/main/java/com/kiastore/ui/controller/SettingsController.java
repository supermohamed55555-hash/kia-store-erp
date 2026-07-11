package com.kiastore.ui.controller;

import com.kiastore.app.Main;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.*;
import java.nio.file.Path;

/**
 * Controller for the Settings screen.
 * Provides Backup (mysqldump) and Restore (mysql) functionality for Admin users.
 */
public class SettingsController {

    private static final String MYSQLDUMP = "C:\\xampp\\mysql\\bin\\mysqldump.exe";
    private static final String MYSQL_CLI = "C:\\xampp\\mysql\\bin\\mysql.exe";
    private static final String DB_NAME   = "kia_store_erp";
    private static final String DB_USER   = "root";
    private static final String DB_PASS   = "";

    @FXML private Label       backupStatusLabel;
    @FXML private ProgressBar backupProgress;
    @FXML private Button      btnBackup;
    @FXML private Button      btnRestore;

    // ─── Backup ────────────────────────────────────────────────────────────────

    @FXML
    private void onBackup() {
        if (!toolExists(MYSQLDUMP)) {
            showError("ملف mysqldump.exe غير موجود في المسار:\n" + MYSQLDUMP +
                    "\nيرجى التأكد من تثبيت XAMPP.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("حفظ النسخة الاحتياطية");
        fc.setInitialFileName("kia_store_backup_" + java.time.LocalDate.now() + ".sql");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL Files (*.sql)", "*.sql"));

        File target = fc.showSaveDialog(btnBackup.getScene().getWindow());
        if (target == null) return;

        setUiWorking(true, "جارٍ إنشاء النسخة الاحتياطية...");

        Thread worker = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    MYSQLDUMP,
                    "-u", DB_USER,
                    "--databases", DB_NAME,
                    "--result-file=" + target.getAbsolutePath()
                );
                if (!DB_PASS.isEmpty()) {
                    pb.command().add(2, "-p" + DB_PASS);
                }
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                // Read output for status
                StringBuilder log = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) log.append(line).append("\n");
                }

                int exitCode = proc.waitFor();
                if (exitCode == 0) {
                    Platform.runLater(() -> {
                        setUiWorking(false, "");
                        showSuccess("✅  تم إنشاء النسخة الاحتياطية بنجاح!\n" + target.getAbsolutePath());
                    });
                } else {
                    String err = log.toString();
                    Platform.runLater(() -> {
                        setUiWorking(false, "");
                        showError("فشل إنشاء النسخة الاحتياطية (exit " + exitCode + "):\n" + err);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setUiWorking(false, "");
                    showError("خطأ غير متوقع:\n" + e.getMessage());
                });
            }
        }, "BackupWorker");
        worker.setDaemon(true);
        worker.start();
    }

    // ─── Restore ───────────────────────────────────────────────────────────────

    @FXML
    private void onRestore() {
        if (!toolExists(MYSQL_CLI)) {
            showError("ملف mysql.exe غير موجود في المسار:\n" + MYSQL_CLI +
                    "\nيرجى التأكد من تثبيت XAMPP.");
            return;
        }

        // Confirm dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الاستعادة");
        confirm.setHeaderText("⚠️  تحذير: ستُستبدل قاعدة البيانات الحالية بالكامل!");
        confirm.setContentText("سيتم استيراد ملف النسخة الاحتياطية وإعادة تشغيل التطبيق.\n" +
                "هذا الإجراء لا يمكن التراجع عنه. هل تريد المتابعة؟");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("اختر ملف النسخة الاحتياطية");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL Files (*.sql)", "*.sql"));

        File source = fc.showOpenDialog(btnRestore.getScene().getWindow());
        if (source == null) return;

        setUiWorking(true, "جارٍ استعادة قاعدة البيانات...");

        Thread worker = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    MYSQL_CLI,
                    "-u", DB_USER,
                    DB_NAME
                );
                if (!DB_PASS.isEmpty()) {
                    pb.command().add(2, "-p" + DB_PASS);
                }
                pb.redirectInput(source);
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                StringBuilder log = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) log.append(line).append("\n");
                }

                int exitCode = proc.waitFor();
                if (exitCode == 0) {
                    Platform.runLater(() -> {
                        setUiWorking(false, "");
                        Alert ok = new Alert(Alert.AlertType.INFORMATION,
                            "✅  تمت استعادة قاعدة البيانات بنجاح!\nسيتم إعادة تشغيل التطبيق الآن.");
                        ok.showAndWait();
                        restartApp();
                    });
                } else {
                    String err = log.toString();
                    Platform.runLater(() -> {
                        setUiWorking(false, "");
                        showError("فشل الاستعادة (exit " + exitCode + "):\n" + err);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setUiWorking(false, "");
                    showError("خطأ غير متوقع:\n" + e.getMessage());
                });
            }
        }, "RestoreWorker");
        worker.setDaemon(true);
        worker.start();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void setUiWorking(boolean working, String status) {
        btnBackup.setDisable(working);
        btnRestore.setDisable(working);
        backupProgress.setVisible(working);
        if (working) {
            backupProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            backupStatusLabel.setText(status);
            backupStatusLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 12; -fx-text-fill: #3B82F6;");
        } else {
            backupProgress.setProgress(0);
            backupStatusLabel.setText(status);
        }
    }

    private boolean toolExists(String path) {
        return new File(path).exists();
    }

    private void showSuccess(String msg) {
        backupStatusLabel.setText(msg);
        backupStatusLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 12; -fx-text-fill: #16A34A;");
        new Alert(Alert.AlertType.INFORMATION, msg).show();
    }

    private void showError(String msg) {
        backupStatusLabel.setText(msg);
        backupStatusLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 12; -fx-text-fill: #DC2626;");
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    /**
     * Restarts the JavaFX application by launching a new process and exiting the current one.
     */
    private void restartApp() {
        try {
            String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String cp = System.getProperty("java.class.path");
            String mainClass = Main.class.getName();
            new ProcessBuilder(javaBin, "-cp", cp, mainClass).start();
        } catch (Exception ignored) {}
        Platform.exit();
    }
}
