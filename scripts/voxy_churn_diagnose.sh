#!/bin/bash
# Focused churn diagnostics for Voxy/Iris/Embeddium logs.
# Usage:
#   ./scripts/voxy_churn_diagnose.sh [path/to/latest.log] [timeline_lines]

set -euo pipefail

detect_log_file() {
  local candidates=(
    "latest.log"
    "logs/latest.log"
    ".reference/craftoria-logs/latest.log"
    ".tmp/logs/latest-craftoria-aftertest.log"
  )
  local c
  for c in "${candidates[@]}"; do
    if [[ -f "$c" ]]; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

LOG_FILE="${1:-}"
TIMELINE_LINES="${2:-40}"

if [[ -z "$LOG_FILE" ]]; then
  if ! LOG_FILE="$(detect_log_file)"; then
    echo "[ERROR] No log file found. Pass one explicitly: ./scripts/voxy_churn_diagnose.sh /path/to/latest.log" >&2
    exit 1
  fi
fi

if [[ ! -f "$LOG_FILE" ]]; then
  echo "[ERROR] Log file not found: $LOG_FILE" >&2
  exit 1
fi

if ! [[ "$TIMELINE_LINES" =~ ^[0-9]+$ ]]; then
  echo "[ERROR] timeline_lines must be an integer, got: $TIMELINE_LINES" >&2
  exit 1
fi

echo "== Voxy Churn Diagnose =="
echo "file: $LOG_FILE"
echo

LC_ALL=C awk '
BEGIN {
  destroy = 0
  create = 0
  shader_fail = 0
  shader_compile_err = 0
  worker_stop = 0
  worker_start = 0
  voxy_recreate = 0
  voxy_created = 0
  section_diag_lines = 0
  max_b2u = 0
  max_u2b = 0
  max_add_q = 0
  max_rem_q = 0
  max_tracked = 0
  max_total_transitions = 0
  section_churn_spikes = 0
}

function val(tok,    a) {
  split(tok, a, "=")
  return a[2] + 0
}

/\[Iris\/\]: Destroying pipeline / { destroy++ }
/\[Iris\/\]: Creating pipeline / { create++ }
/Failed to create shader rendering pipeline, disabling shaders!/ { shader_fail++ }
/Shader compilation log for iris:embeddium-shader-translucent:/ { shader_compile_err++ }
/\[ChunkBuilder\/\]: Stopping worker threads/ { worker_stop++ }
/\[ChunkBuilder\/\]: Started [0-9]+ worker threads/ { worker_start++ }
/\[VoxyRecreate\]/ { voxy_recreate++ }
/Voxy render system created with / { voxy_created++ }

/\[VoxyDiag\] sectionTransitions / {
  section_diag_lines++
  b2u = 0
  u2b = 0
  addq = 0
  remq = 0
  tracked = 0
  n = split($0, t, /[[:space:]]+/)
  for (i = 1; i <= n; i++) {
    if (t[i] ~ /^builtToUnbuilt=/) b2u = val(t[i])
    else if (t[i] ~ /^unbuiltToBuilt=/) u2b = val(t[i])
    else if (t[i] ~ /^chunkBoundAddQ=/) addq = val(t[i])
    else if (t[i] ~ /^chunkBoundRemQ=/) remq = val(t[i])
    else if (t[i] ~ /^chunkBoundTracked=/) tracked = val(t[i])
  }
  total = b2u + u2b
  if (b2u > max_b2u) max_b2u = b2u
  if (u2b > max_u2b) max_u2b = u2b
  if (addq > max_add_q) max_add_q = addq
  if (remq > max_rem_q) max_rem_q = remq
  if (tracked > max_tracked) max_tracked = tracked
  if (total > max_total_transitions) max_total_transitions = total
  if (total >= 200 || remq >= 100 || addq >= 100) section_churn_spikes++
}

END {
  print "[COUNTS]"
  print "  iris_destroy_pipeline=" destroy
  print "  iris_create_pipeline=" create
  print "  iris_shader_compile_error_lines=" shader_compile_err
  print "  iris_pipeline_fail_disable_shaders=" shader_fail
  print "  chunkbuilder_stop=" worker_stop
  print "  chunkbuilder_start=" worker_start
  print "  voxy_renderer_recreate_events=" voxy_recreate
  print "  voxy_renderer_created=" voxy_created
  print "  voxy_section_diag_lines=" section_diag_lines
  print ""

  print "[SECTION_CHURN_PEAKS]"
  print "  max_builtToUnbuilt=" max_b2u
  print "  max_unbuiltToBuilt=" max_u2b
  print "  max_total_transitions=" max_total_transitions
  print "  max_chunkBoundAddQ=" max_add_q
  print "  max_chunkBoundRemQ=" max_rem_q
  print "  max_chunkBoundTracked=" max_tracked
  print "  section_churn_spike_intervals=" section_churn_spikes
  print ""

  print "[DIAGNOSIS_HINT]"
  if (shader_fail > 0 || shader_compile_err > 0) {
    print "  shader_pipeline_unstable=yes"
  } else {
    print "  shader_pipeline_unstable=no"
  }
  if ((destroy + create) >= 4) {
    print "  pipeline_rebuild_churn=high"
  } else if ((destroy + create) > 0) {
    print "  pipeline_rebuild_churn=present"
  } else {
    print "  pipeline_rebuild_churn=none"
  }
  if (section_diag_lines > 0 && (max_total_transitions >= 200 || max_rem_q >= 100 || max_add_q >= 100)) {
    print "  section_transition_churn=high"
  } else if (section_diag_lines > 0 && max_total_transitions > 0) {
    print "  section_transition_churn=present"
  } else if (section_diag_lines == 0) {
    print "  section_transition_churn=unknown (no [VoxyDiag] sectionTransitions lines found)"
  } else {
    print "  section_transition_churn=none"
  }
}
' "$LOG_FILE"

echo
echo "[TIMELINE_LAST_${TIMELINE_LINES}]"
rg -n -i \
  "Destroying pipeline|Creating pipeline|Failed to create shader rendering pipeline|Shader compilation log for iris:embeddium-shader-translucent|Stopping worker threads|Started [0-9]+ worker threads|\\[VoxyDiag\\] sectionTransitions|\\[VoxyRecreate\\]|Voxy render system created with|Using shaderpack:" \
  "$LOG_FILE" | tail -n "$TIMELINE_LINES" || true

