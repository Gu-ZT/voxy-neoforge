#!/bin/bash
# Summarize VOXY_PERF entries from latest.log-style files.
# Usage: ./scripts/perf_latest_summary.sh [/path/to/latest.log]

set -euo pipefail

LOG_FILE="${1:-latest.log}"
if [[ ! -f "$LOG_FILE" ]]; then
  echo "[ERROR] Log file not found: $LOG_FILE" >&2
  exit 1
fi

LC_ALL=C awk '
function reset_map(   k) { for (k in map) delete map[k] }
function load_map(line,   i,n,a,kv) {
  reset_map()
  n = split(line, a, /[[:space:]]+/)
  for (i = 1; i <= n; i++) {
    if (index(a[i], "=") > 0) {
      split(a[i], kv, "=")
      map[kv[1]] = kv[2]
    }
  }
}
/VOXY_PERF upload_stream/ { upload_line = $0 }
/VOXY_PERF world_section_cache/ { section_line = $0 }
/VOXY_PERF async_node/ { async_line = $0 }
END {
  if (upload_line == "" && section_line == "" && async_line == "") {
    print "[INFO] No VOXY_PERF lines found in " FILENAME
    exit 0
  }

  if (upload_line != "") {
    load_map(upload_line)
    print "[UPLOAD_STREAM]"
    print "  coherent=" map["coherent"]
    print "  remaining_bytes=" map["remaining_bytes"]
    print "  threshold_bytes=" map["threshold_bytes"]
    print "  used_bytes=" map["used_bytes"]
    print "  queued_frames=" map["queued_frames"]
    print "  pending_copies=" map["pending_copies"]
    print "  glfinish_stalls=" map["glfinish_stalls"]
    print "  backpressure_observations=" map["backpressure_observations"]
  }

  if (section_line != "") {
    load_map(section_line)
    print "[WORLD_SECTION_CACHE]"
    print "  pool_size=" map["pool_size"]
    print "  allocations=" map["allocations"]
    print "  hits=" map["hits"]
    print "  misses=" map["misses"]
    print "  hit_pct=" map["hit_pct"]
    print "  offers=" map["offers"]
    print "  rejects=" map["rejects"]
  }

  if (async_line != "") {
    load_map(async_line)
    print "[ASYNC_NODE]"
    print "  copy_batches=" map["copy_batches"]
    print "  avg_copy_batch=" map["avg_copy_batch"]
    print "  max_copy_batch=" map["max_copy_batch"]
    print "  copy_dispatch_batches=" map["copy_dispatch_batches"]
    print "  avg_copy_dispatched_per_tick=" map["avg_copy_dispatched_per_tick"]
    print "  max_copy_dispatched_per_tick=" map["max_copy_dispatched_per_tick"]
    print "  pending_copy_remaining=" map["pending_copy_remaining"]
    print "  pending_result_age_ticks=" map["pending_result_age_ticks"]
    print "  result_copy_entries=" map["result_copy_entries"]
    print "  result_scatter_entries=" map["result_scatter_entries"]
    print "  copy_budget_per_tick=" map["copy_budget_per_tick"]
    print "  chunked_copy_enabled=" map["chunked_copy_enabled"]
    print "  sync_wait_events=" map["sync_wait_events"]
    print "  sync_wait_threshold_copies=" map["sync_wait_threshold_copies"]
    print "  warn_threshold_copies=" map["warn_threshold_copies"]
  }
}
' "$LOG_FILE"
