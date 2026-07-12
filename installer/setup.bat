@echo off
chcp 65001 >nul
title تثبيت نظام KIA Store ERP

:: Check for administrative privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ===================================================
    echo جاري طلب صلاحيات المسؤول (Administrator)...
    echo ===================================================
    goto UACPrompt
) else ( goto gotAdmin )

:UACPrompt
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    echo UAC.ShellExecute "cmd.exe", "/c """%~s0"""", "", "runas", 1 >> "%temp%\getadmin.vbs"
    "%temp%\getadmin.vbs"
    del "%temp%\getadmin.vbs"
    exit /B

:gotAdmin
    pushd "%~dp0"

echo ===================================
echo    تثبيت نظام KIA Store ERP
echo ===================================
echo.

:: 1. Check for Java
echo [1/7] التحقق من وجود الجافا (Java)...
java -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo =======================================================================
    echo خطأ: الجافا غير مثبتة على هذا الجهاز!
    echo يرجى تثبيت Java أولاً من ملف jdk-installer.exe الموجود في نفس المجلد.
    echo =======================================================================
    echo.
    pause
    exit /b 1
)
echo تم العثور على الجافا بنجاح.
echo.

:: 2. Check and Install XAMPP
echo [2/7] التحقق من ملف تثبيت قاعدة البيانات...
if not exist xampp-installer.exe (
    echo.
    echo =======================================================================
    echo خطأ: ملف xampp-installer.exe غير موجود في مجلد التثبيت!
    echo يرجى تحميله أولاً ووضعه في نفس المجلد بجانب ملف setup.bat.
    echo =======================================================================
    echo.
    pause
    exit /b 1
)

echo جاري تثبيت قاعدة البيانات (XAMPP - MySQL) صامتاً...
echo قد يستغرق هذا بضع دقائق، يرجى الانتظار...
xampp-installer.exe --mode unattended --disable-components apache,filezilla,mercury,tomcat
echo تم تثبيت قاعدة البيانات بنجاح.
echo.

:: 3. Configure and Start MySQL Service
echo [3/7] ضبط خدمة قاعدة البيانات وتشغيلها...
sc query MySQL >nul 2>&1
if errorlevel 1 (
    echo تسجيل MySQL كخدمة في النظام...
    "C:\xampp\mysql\bin\mysqld.exe" --install MySQL
)
echo جاري تشغيل الخدمة...
net start MySQL >nul 2>&1
timeout /t 5 >nul
echo تم تشغيل قاعدة البيانات بنجاح.
echo.

:: 4. Create Database
echo [4/7] تهيئة قاعدة بيانات النظام...
"C:\xampp\mysql\bin\mysql.exe" -u root -e "CREATE DATABASE IF NOT EXISTS kia_store_erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if errorlevel 1 (
    echo خطأ: فشل الاتصال بقاعدة البيانات لإنشاء database.
    pause
    exit /b 1
)
echo تم إنشاء قاعدة البيانات.
echo.

:: 5. Import Schema & Seed Data
echo [5/7] جاري استيراد الجداول والبيانات الأساسية...
"C:\xampp\mysql\bin\mysql.exe" -u root kia_store_erp < schema.sql
if errorlevel 1 (
    echo خطأ: فشل استيراد الجداول (schema.sql).
    pause
    exit /b 1
)
"C:\xampp\mysql\bin\mysql.exe" -u root kia_store_erp < seed.sql
if errorlevel 1 (
    echo خطأ: فشل استيراد البيانات الأساسية (seed.sql).
    pause
    exit /b 1
)
echo تم استيراد الجداول والبيانات بنجاح.
echo.

:: 6. Copy Files to Program Files
echo [6/7] تثبيت ملفات البرنامج...
mkdir "C:\Program Files\KIA Store ERP" >nul 2>&1
copy /Y "kia-store-erp.jar" "C:\Program Files\KIA Store ERP\" >nul
copy /Y "kia-store.ico" "C:\Program Files\KIA Store ERP\" >nul
copy /Y "start-kia.bat" "C:\Program Files\KIA Store ERP\" >nul
echo تم نسخ ملفات البرنامج بنجاح.
echo.

:: 7. Create Shortcuts
echo [7/7] إنشاء الاختصارات على سطح المكتب وقائمة ابدأ...
:: Desktop Shortcut
powershell -Command "$WshShell = New-Object -comObject WScript.Shell; $DesktopPath = $WshShell.SpecialFolders('Desktop'); $Shortcut = $WshShell.CreateShortcut(\"$DesktopPath\KIA Store ERP.lnk\"); $Shortcut.TargetPath = 'C:\Program Files\KIA Store ERP\start-kia.bat'; $Shortcut.IconLocation = 'C:\Program Files\KIA Store ERP\kia-store.ico'; $Shortcut.WorkingDirectory = 'C:\Program Files\KIA Store ERP'; $Shortcut.Save()"

:: Desktop Direct Launcher Bat File (Backup)
copy /Y "start-kia.bat" "%USERPROFILE%\Desktop\start-kia.bat" >nul

:: Start Menu Shortcut
powershell -Command "$WshShell = New-Object -comObject WScript.Shell; $ProgramsPath = $WshShell.SpecialFolders('Programs'); $Shortcut = $WshShell.CreateShortcut(\"$ProgramsPath\KIA Store ERP.lnk\"); $Shortcut.TargetPath = 'C:\Program Files\KIA Store ERP\start-kia.bat'; $Shortcut.IconLocation = 'C:\Program Files\KIA Store ERP\kia-store.ico'; $Shortcut.WorkingDirectory = 'C:\Program Files\KIA Store ERP'; $Shortcut.Save()"

echo ===================================
echo    تم التثبيت بنجاح!
echo    يمكنك تشغيل البرنامج من اختصار سطح المكتب: "KIA Store ERP"
echo ===================================
echo.
pause
exit
