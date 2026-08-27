#!/bin/sh
# 本地快速单测：只跑不依赖 Robolectric 的纯 JVM 测试
# 本机是 aarch64，Robolectric native runtime 不支持 Linux ARM64，
# 所以带 org.robolectric 依赖的测试类会被自动跳过（它们只能在 x86_64 的 CI 上跑）。
# 用法: ./scripts/unit-test-local.sh
set -e
cd "$(dirname "$0")/.."

GRADLE=/opt/gradle-9.6.1/bin/gradle
export GRADLE_USER_HOME=/workspace/tools/gradle-home

TESTS_ARGS=""
COUNT=0
SKIP=0
for f in $(find app/src/test -name "*Test.kt" | sort); do
  if grep -qE "org\.robolectric|RobolectricTestRunner" "$f"; then
    SKIP=$((SKIP+1))
    continue
  fi
  pkg=$(grep -m1 '^package ' "$f" | sed 's/package //;s/;//' | tr -d ' ')
  cls=$(basename "$f" .kt)
  TESTS_ARGS="$TESTS_ARGS --tests $pkg.$cls"
  COUNT=$((COUNT+1))
done
echo "== 纯 JVM 测试类: $COUNT 个，跳过 Robolectric 类: $SKIP 个 =="
LOG=/tmp/eta-unit-test.log
# 独立会话后台执行，脱离调用方超时/进程组 kill（与构建脚本同模式）
# shellcheck disable=SC2086
setsid sh -c "GRADLE_USER_HOME=$GRADLE_USER_HOME $GRADLE --no-daemon \
  -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' \
  -Dkotlin.daemon.jvm.options=-Xmx1024m \
  -Peta.mirror=true :app:testDebugUnitTest $TESTS_ARGS > $LOG 2>&1" < /dev/null &
echo "== 单测已启动 (PID $!) | 日志: $LOG =="

# 轮询直到出结果（最长 10 分钟）
i=0
while [ $i -lt 120 ]; do
  if grep -qE "BUILD SUCCESSFUL|BUILD FAILED" "$LOG" 2>/dev/null; then
    tail -5 "$LOG"
    if grep -q "BUILD FAILED" "$LOG"; then
      echo "!! 测试失败，完整日志: $LOG"; exit 1
    fi
    echo "== 单测通过 =="
    exit 0
  fi
  sleep 5; i=$((i+1))
done
echo "!! 10 分钟未见结果，请查看: $LOG"; exit 1
