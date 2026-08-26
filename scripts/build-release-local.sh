#!/bin/sh
# 本机构建签名 Release APK（aarch64 优化版）
# 签名来源：keystore/eta-release.jks + keystore/keystore.properties（均已被 .gitignore 排除）
# CI 请走 .github/workflows/android-release.yml（secrets 注入，互不影响）
set -e
cd "$(dirname "$0")/.."

KS=keystore/eta-release.jks
PROPS=keystore/keystore.properties
[ -f "$KS" ] || { echo "!! 缺少 $KS，请先运行 keystore 生成步骤"; exit 1; }
[ -f "$PROPS" ] || { echo "!! 缺少 $PROPS"; exit 1; }

STORE_PASS=$(grep '^storePassword=' "$PROPS" | cut -d= -f2)
KEY_ALIAS=$(grep '^keyAlias=' "$PROPS" | cut -d= -f2)
KEY_PASS=$(grep '^keyPassword=' "$PROPS" | cut -d= -f2)

export ETA_RELEASE_STORE_FILE="$PWD/$KS"
export ETA_RELEASE_STORE_PASSWORD="$STORE_PASS"
export ETA_RELEASE_KEY_ALIAS="$KEY_ALIAS"
export ETA_RELEASE_KEY_PASSWORD="$KEY_PASS"
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-/workspace/tools/gradle-home}
LOG=/tmp/eta-release-build.log
: > "$LOG"  # 清空旧日志，避免轮询读到上次结果

# 清理上次构建残留的 daemon，释放内存（R8 全量 minify 内存敏感）
pkill -f "org.gradle.launcher.daemon.bootstrap.GradleDaemon" 2>/dev/null || true
pkill -f "KotlinCompileDaemon" 2>/dev/null || true
sleep 2

# 独立会话启动（脱离调用方超时/进程组 kill），R8 给足 4G
setsid sh -c "cd '$PWD' && GRADLE_USER_HOME=$GRADLE_USER_HOME /opt/gradle-9.6.1/bin/gradle --no-daemon \
  -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' \
  -Dkotlin.daemon.jvm.options=-Xmx2048m \
  -Dorg.gradle.internal.http.connectionTimeout=15000 \
  -Dorg.gradle.internal.http.socketTimeout=15000 \
  -Dorg.gradle.internal.repository.max.retries=1 \
  -Peta.mirror=true :app:assembleRelease --console=plain > $LOG 2>&1" < /dev/null &
echo "== Release 构建已启动 (PID $!) | 日志: $LOG =="

i=0
while [ $i -lt 240 ]; do
  if grep -qE "BUILD SUCCESSFUL|BUILD FAILED" "$LOG" 2>/dev/null; then
    tail -3 "$LOG"
    if grep -q "BUILD FAILED" "$LOG"; then
      echo "!! 构建失败，完整日志: $LOG"; exit 1
    fi
    APK="app/build/outputs/apk/release/app-release.apk"
    [ -f "$APK" ] || APK=$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
    echo "== Release 构建成功: $APK ($(du -h "$APK" 2>/dev/null | cut -f1)) =="
    exit 0
  fi
  sleep 5; i=$((i+1))
done
echo "!! 20 分钟未见结果，请查看: $LOG"; exit 1
