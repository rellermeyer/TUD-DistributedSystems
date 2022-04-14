#!/bin/bash
# we 're using this script because with akka actors there is no straightforward way to manage actively the number of
# spawned actors (which corresponds to the number of processes for the experiments)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/../target/scala-2.13/revc-implementation-assembly-0.1.0-SNAPSHOT.jar"
CSV_DIR="$SCRIPT_DIR/csv"
CLOCKS=( "VC" "EVC" "REVC" "DMTREVC" )

MAX_MSG=200
MIN_PROCESSES=2
STEP_PROCESSES=2
MAX_PROCESSES=24
N_RUNS=30

mkdir -p "$CSV_DIR"/{time,bitsizes}

cd "$SCRIPT_DIR/.." || { echo "cd failed"; exit 1; }

# build
sbt clean assembly > /dev/null || { echo "build failed"; exit 1; }

for clock in "${CLOCKS[@]}"; do
  : > "$CSV_DIR/time/$clock.csv"
  for processes in $(seq $MIN_PROCESSES $STEP_PROCESSES $MAX_PROCESSES); do
    for run in $(seq 1 $N_RUNS); do
      echo run $run: clock $clock with $processes processes
      scala $JAR false $MAX_MSG $processes $clock 2>/dev/null \
        | grep duration \
        | awk -v p="$processes" -v r="$run" '{printf "%s,%s,%s\n", r, p, $2}' \
        >> "$CSV_DIR/time/$clock.csv"
    done
  done
done


for clock in "${CLOCKS[@]}"; do
  : > "$CSV_DIR/bitsizes/$clock.csv"
  for run in $(seq 1 $N_RUNS); do
    echo run $run: clock $clock with bitsize tracking
    scala $JAR true $MAX_MSG $MAX_PROCESSES $clock 2>/dev/null \
      | grep eventnum \
      | awk -v r="$run" '{printf "%s,%s,%s\n", r, $2, $3}' \
      >> "$CSV_DIR/bitsizes/$clock.csv"
  done
done

cd "$SCRIPT_DIR" || { echo "cd failed"; exit 1; }

python plot.py # > "$SCRIPT_DIR/descriptive_stats.txt"
