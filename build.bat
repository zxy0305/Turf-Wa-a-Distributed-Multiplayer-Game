@echo off
rem Builds TurfWarClient.jar and TurfWarServer.jar into dist\. Needs Java 17 or later.
cd /d "%~dp0"
if exist out rmdir /s /q out
if exist dist rmdir /s /q dist
mkdir out
mkdir dist
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 exit /b 1
del sources.txt
jar cfe dist\TurfWarClient.jar turfwar.Main -C out .
jar cfe dist\TurfWarServer.jar turfwar.server.ServerMain -C out .
echo Built dist\TurfWarClient.jar and dist\TurfWarServer.jar
