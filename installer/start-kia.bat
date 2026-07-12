@echo off
REM Check if MySQL is running
sc query MySQL | find "RUNNING" >nul
if errorlevel 1 (
    echo جاري تشغيل قاعدة البيانات...
    net start MySQL
    timeout /t 3 >nul
)
REM Launch the app
start "" javaw -jar "C:\Program Files\KIA Store ERP\kia-store-erp.jar"
exit
