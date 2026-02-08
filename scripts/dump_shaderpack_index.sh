#!/bin/bash
# Dump remote Prism shaderpack inventory + program index to local .tmp files.
# Usage: ./scripts/dump_shaderpack_index.sh [instance]

set -euo pipefail

SSH_HOST="${SSH_HOST:-192.168.178.206}"
SSH_USER="${SSH_USER:-shelfwood}"
INSTANCE_NAME="${1:-1.21.1}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
OUT_DIR="${PROJECT_DIR}/.tmp"
mkdir -p "${OUT_DIR}"

INV_OUT="${OUT_DIR}/shaderpacks_inventory.txt"
IDX_OUT="${OUT_DIR}/shaderpacks_program_index.txt"

WIN_SP="C:\\Users\\${SSH_USER}\\AppData\\Roaming\\PrismLauncher\\instances\\${INSTANCE_NAME}\\minecraft\\shaderpacks"

echo "[INFO] Target: ${SSH_USER}@${SSH_HOST}"
echo "[INFO] Instance: ${INSTANCE_NAME}"
echo "[INFO] Shaderpack dir: ${WIN_SP}"

echo "[STEP] Inventory -> ${INV_OUT}"
ssh -o ConnectTimeout=8 "${SSH_USER}@${SSH_HOST}" \
  "powershell -NoProfile -Command \"\
if (Test-Path '${WIN_SP}') { \
  Get-ChildItem -Path '${WIN_SP}' -Force | \
    Select-Object Name,Length,LastWriteTime,Extension | \
    Format-Table -AutoSize | Out-String -Width 4096 \
} else { \
  Write-Output 'MISSING_SHADERPACK_DIR'; \
  Write-Output '${WIN_SP}'; \
}\"" \
  > "${INV_OUT}" 2>&1

echo "[STEP] Program index -> ${IDX_OUT}"
ssh -o ConnectTimeout=8 "${SSH_USER}@${SSH_HOST}" \
  "powershell -NoProfile -Command \"\
if (Test-Path '${WIN_SP}') { \
  Add-Type -AssemblyName System.IO.Compression.FileSystem; \
  Get-ChildItem -Path '${WIN_SP}' -Filter *.zip | ForEach-Object { \
    Write-Output ('=== ' + \$_.Name + ' ==='); \
    try { \
      \$z = [System.IO.Compression.ZipFile]::OpenRead(\$_.FullName); \
      \$z.Entries | Where-Object { \
        \$_.FullName -match '(^|/)(gbuffers|dh_|world|shadow|composite|deferred|voxy).*\.(fsh|vsh|glsl|json)$' \
      } | ForEach-Object { \$_.FullName }; \
      \$z.Dispose(); \
    } catch { \
      Write-Output ('[ZIP_READ_ERROR] ' + \$_.Name + ': ' + \$_.Exception.Message); \
    } \
    Write-Output ''; \
  } \
} else { \
  Write-Output 'MISSING_SHADERPACK_DIR'; \
  Write-Output '${WIN_SP}'; \
}\"" \
  > "${IDX_OUT}" 2>&1

echo "[DONE] Wrote:"
ls -lh "${INV_OUT}" "${IDX_OUT}"
