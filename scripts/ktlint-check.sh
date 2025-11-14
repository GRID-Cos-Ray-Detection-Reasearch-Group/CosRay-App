#!/usr/bin/env bash
set -euo pipefail

if [ -x "./gradlew" ]; then
  ./gradlew ktlintCheck
elif [ -f "./gradlew.bat" ]; then
  ./gradlew.bat ktlintCheck
else
  echo "Gradle wrapper not found" >&2
  exit 1
fi
