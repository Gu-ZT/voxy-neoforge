#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <latest.log>"
  exit 1
fi

LOG_FILE="$1"
if [[ ! -f "$LOG_FILE" ]]; then
  echo "error: file not found: $LOG_FILE"
  exit 1
fi

echo "[voxy_perf_report] file=$LOG_FILE"

echo
echo "== Async Node Perf =="
awk '
  /VOXY_PERF async_node/ {
    count++
    for (i=1; i<=NF; i++) {
      if ($i ~ /sync_wait_events=/) {
        split($i,a,"="); v=a[2]+0
        if (count==1 || v<minSync) minSync=v
        if (count==1 || v>maxSync) maxSync=v
      } else if ($i ~ /avg_copy_dispatched_per_tick=/) {
        split($i,a,"="); v=a[2]+0
        if (count==1 || v<minAvgDisp) minAvgDisp=v
        if (count==1 || v>maxAvgDisp) maxAvgDisp=v
      } else if ($i ~ /max_copy_dispatched_per_tick=/) {
        split($i,a,"="); v=a[2]+0
        if (count==1 || v<minMaxDisp) minMaxDisp=v
        if (count==1 || v>maxMaxDisp) maxMaxDisp=v
      } else if ($i ~ /copy_budget_per_tick=/) {
        split($i,a,"="); lastBudget=a[2]+0
      }
    }
  }
  END {
    if (count==0) {
      print "no VOXY_PERF async_node lines"
      exit
    }
    printf "samples=%d\n", count
    printf "sync_wait_events[min..max]=%d..%d\n", minSync, maxSync
    printf "avg_copy_dispatched_per_tick[min..max]=%d..%d\n", minAvgDisp, maxAvgDisp
    printf "max_copy_dispatched_per_tick[min..max]=%d..%d\n", minMaxDisp, maxMaxDisp
    printf "copy_budget_per_tick(last)=%d\n", lastBudget
    if (maxMaxDisp >= lastBudget) {
      print "saturation=YES (dispatch hits budget cap)"
    } else {
      print "saturation=NO"
    }
  }
' "$LOG_FILE"

echo
echo "== Upload Stream Perf =="
awk '
  /VOXY_PERF upload_stream/ {
    count++
    for (i=1; i<=NF; i++) {
      if ($i ~ /glfinish_stalls=/) { split($i,a,"="); gl=a[2]+0 }
      else if ($i ~ /backpressure_observations=/) { split($i,a,"="); bp=a[2]+0 }
      else if ($i ~ /remaining_bytes=/) {
        split($i,a,"="); v=a[2]+0
        if (count==1 || v<minRemain) minRemain=v
        if (count==1 || v>maxRemain) maxRemain=v
      } else if ($i ~ /threshold_bytes=/) {
        split($i,a,"="); threshold=a[2]+0
      }
    }
  }
  END {
    if (count==0) {
      print "no VOXY_PERF upload_stream lines"
      exit
    }
    printf "samples=%d\n", count
    printf "remaining_bytes[min..max]=%d..%d\n", minRemain, maxRemain
    printf "threshold_bytes(last)=%d\n", threshold
    printf "glfinish_stalls(last)=%d\n", gl
    printf "backpressure_observations(last)=%d\n", bp
  }
' "$LOG_FILE"

echo
echo "== Quick Flags =="
rg -n "renderOpaque first call|setupViewport: GL_VIEWPORT returned 0x0|VOXY_PERF|Large amount of copies|Cannot use the default framebuffer|shader could not be loaded|Falling back to normal rendering without shaders" "$LOG_FILE" | tail -n 40 || true
