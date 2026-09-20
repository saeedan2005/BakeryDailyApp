#!/usr/bin/env sh
set -eu
GRADLE_VERSION="8.9"
GRADLE_SHA256="d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
BASE_DIR="$GRADLE_USER_HOME/codemagic-wrapper"
DIST_DIR="$BASE_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/bin/gradle"
TMP="$BASE_DIR/gradle-$GRADLE_VERSION-bin.zip"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$BASE_DIR"
  if [ ! -f "$TMP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -fL --retry 3 --retry-delay 2 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$TMP"
  fi
  ACTUAL_SHA256="$(sha256sum "$TMP" | awk '{print $1}')"
  if [ "$ACTUAL_SHA256" != "$GRADLE_SHA256" ]; then
    echo "ERROR: Gradle distribution SHA-256 mismatch."
    echo "Expected: $GRADLE_SHA256"
    echo "Actual:   $ACTUAL_SHA256"
    rm -f "$TMP"
    exit 1
  fi
  rm -rf "$DIST_DIR.tmp" "$DIST_DIR"
  mkdir -p "$DIST_DIR.tmp"
  unzip -q "$TMP" -d "$DIST_DIR.tmp"
  mv "$DIST_DIR.tmp/gradle-$GRADLE_VERSION" "$DIST_DIR"
  rm -rf "$DIST_DIR.tmp"
fi
exec "$GRADLE_BIN" "$@"
