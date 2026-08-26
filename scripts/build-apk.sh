#!/bin/sh
# 本机构建 APK（aarch64 优化版）
# 用法: ./scripts/build-apk.sh [assembleDebug|assembleRelease]  默认 assembleDebug
set -e
cd "$(dirname "$0")/.."

TASK=${1:-assembleDebug}
GRADLE=/opt/gradle-9.6.1/bin/gradle
export GRADLE_USER_HOME=/workspace/tools/gradle-home
LOG=/tmp/eta-build.log

# 1) 清理上次构建残留的 Gradle/Kotlin daemon，释放内存（避免 OOM）
pkill -f "org.gradle.launcher.daemon.bootstrap.GradleDaemon" 2>/dev/null || true
pkill -f "KotlinCompileDaemon" 2>/dev/null || true
sleep 2

# 2) 独立会话启动构建，日志落盘（脱离调用方超时/进程组 kill）
setsid sh -c "GRADLE_USER_HOME=$GRADLE_USER_HOME $GRADLE --no-daemon \
  -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' \
  -Dkotlin.daemon.jvm.options=-Xmx1024m \
  :app:$TASK --console=plain > $LOG 2>&1" < /dev/null &
echo "== 构建已启动 (PID $!) | 任务: :app:$TASK | 日志: $LOG =="

# 3) 轮询直到出结果（最长 10 分钟）
i=0
while [ $i -lt 120 ]; do
  if grep -qE "BUILD SUCCESSFUL|BUILD FAILED" "$LOG" 2>/dev/null; then
    tail -3 "$LOG"
    if grep -q "BUILD FAILED" "$LOG"; then
      echo "!! 构建失败，完整日志: $LOG"; exit 1
    fi
    APK=$(ls -t app/build/outputs/apk/*/app-*.apk 2>/dev/null | head -1)
    echo "== 构建成功: $APK ($(du -h "$APK" 2>/dev/null | cut -f1)) =="
    exit 0
  fi
  sleep 5; i=$((i+1))
done
echo "!! 10 分钟未见结果，构建可能仍在进行，请查看: $LOG"; exit 1
