@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
set "CONAN_HOME=%ROOT%\conan\cache"

conan remove "*#!latest" -c
