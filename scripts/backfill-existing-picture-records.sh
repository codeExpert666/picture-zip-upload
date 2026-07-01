#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_JAR="${APP_JAR:-${PROJECT_DIR}/target/picture-zip-upload-0.0.1-SNAPSHOT.jar}"

# 将脚本友好的参数名转换成 Spring Boot 配置参数。
translate_args() {
  local translated=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --business-area|--operator|--batch-id|--legacy-root|--limit|--dry-run)
        local key="${1#--}"
        translated+=("--picture-maintenance.${key}=$2")
        shift 2
        ;;
      --business-area=*|--operator=*|--batch-id=*|--legacy-root=*|--limit=*|--dry-run=*)
        local key="${1%%=*}"
        local value="${1#*=}"
        key="${key#--}"
        translated+=("--picture-maintenance.${key}=${value}")
        shift
        ;;
      *)
        translated+=("$1")
        shift
        ;;
    esac
  done
  if ((${#translated[@]} > 0)); then
    printf '%s\n' "${translated[@]}"
  fi
}

if [[ ! -f "${APP_JAR}" ]]; then
  (cd "${PROJECT_DIR}" && mvn -DskipTests package)
fi

mapfile -t EXTRA_ARGS < <(translate_args "$@")

# 维护任务只需要启动 Spring 容器和 MyBatis，不需要打开 HTTP 端口。
exec java -jar "${APP_JAR}" \
  --spring.main.web-application-type=none \
  --picture-maintenance.enabled=true \
  --picture-maintenance.mode=BACKFILL_EXISTING \
  "${EXTRA_ARGS[@]}"
