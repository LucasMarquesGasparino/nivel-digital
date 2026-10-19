#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT_TEST="$PROJECT_DIR/build/test"
if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC=javac
    JAVA=java
fi
rm -rf "$OUT_TEST"
mkdir -p "$OUT_TEST"
"$JAVAC" -encoding UTF-8 -d "$OUT_TEST" \
    "$PROJECT_DIR/src/br/com/lucasgasparino/niveldigital/LevelMath.java" \
    "$PROJECT_DIR/tests/LevelMathTest.java"
"$JAVA" -cp "$OUT_TEST" br.com.lucasgasparino.niveldigital.LevelMathTest
