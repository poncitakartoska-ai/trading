@echo off
chcp 65001 >nul
echo ============================================================================
echo   MOEX Pulse AI • Quant 2.0 Desktop — Сборка автономного .EXE для Windows
echo ============================================================================
python -m pip install --upgrade pip pyinstaller
python -m PyInstaller --noconfirm --onefile --windowed --name "MOEX_Pulse_AI_Desktop" moex_pulse_pc.py
echo.
echo Готово! Ваш файл находится в папке: dist\MOEX_Pulse_AI_Desktop.exe
pause
