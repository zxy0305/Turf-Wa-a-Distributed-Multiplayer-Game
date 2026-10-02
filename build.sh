#!/bin/sh
# Builds TurfWarClient.jar and TurfWarServer.jar into dist/. Needs Java 17 or later.
set -e
cd "$(dirname "$0")"
rm -rf out dist
mkdir -p out dist
javac -d out $(find src -name '*.java')
jar cfe dist/TurfWarClient.jar turfwar.Main -C out .
jar cfe dist/TurfWarServer.jar turfwar.server.ServerMain -C out .
echo "Built dist/TurfWarClient.jar and dist/TurfWarServer.jar"
