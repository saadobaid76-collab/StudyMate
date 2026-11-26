#!/usr/bin/env sh
# Small self-bootstrapping Gradle launcher. The official wrapper JAR is intentionally
# not required; Gradle 8.13 is downloaded once into the user's Gradle cache.
set -eu
GRADLE_VERSION="8.13"
DIST_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/manual-dists/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${DIST_DIR}/gradle-${GRADLE_VERSION}/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$DIST_DIR"
  ARCHIVE="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
  if [ ! -f "$ARCHIVE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ARCHIVE" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    else
      echo "Please install curl or wget to download Gradle." >&2
      exit 1
    fi
  fi
  unzip -q -o "$ARCHIVE" -d "$DIST_DIR"
fi
exec "$GRADLE_BIN" "$@"
