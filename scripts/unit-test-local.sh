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
# shellcheck disable=SC2086
exec $GRADLE --no-daemon :app:testDebugUnitTest $TESTS_ARGS
