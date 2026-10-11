#!/usr/bin/env bash
# measure-android.sh <apk> <label>: 15 cold starts (am start -W -S, median) and memory (PSS,
# native heap) on the Welcome screen, on $ANDROID_SERIAL. Used for the KMP vs Rust numbers.
apk="$1"; label="$2"; pkg=com.backtosafety.app; act="$pkg/.MainActivity"
adb uninstall $pkg >/dev/null 2>&1 || true
adb install "$apk" >/dev/null
adb shell am start -W -S -n $act >/dev/null; sleep 5   # first launch creates the database
times=()
for i in $(seq 1 15); do
  t=$(adb shell am start -W -S -n $act | awk -F': ' '/TotalTime/ {print $2}' | tr -d '\r')
  times+=("$t"); sleep 2
done
sleep 5
pss=$(adb shell dumpsys meminfo $pkg | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / && !p {print $2; p=1}' | head -1)
native=$(adb shell dumpsys meminfo $pkg | awk '/Native Heap:/ {print $3; exit}')
sorted=$(printf '%s\n' "${times[@]}" | sort -n | tr '\n' ' ')
median=$(printf '%s\n' "${times[@]}" | sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}')
echo "$label: cold start median ${median} ms (all: $sorted) | PSS ${pss} KB, native heap ${native} KB"
