#!/bin/bash
cd /workspace/LiquidBox
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export GRADLE_USER_HOME=/root/.gradle_lb
export PATH=$JAVA_HOME/bin:$PATH
GRADLE=/opt/gradle-9.7.1/bin/gradle
echo "Gradle: $($GRADLE --version | grep '^Gradle' | head -1)"
$GRADLE "$@" --no-daemon --stacktrace
