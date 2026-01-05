#!/usr/bin/env sh
# Minimal Gradle wrapper script (demo project)
DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_CMD="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_CMD" ]; then
  JAVA_CMD="java"
fi
exec "$JAVA_CMD" -cp "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
