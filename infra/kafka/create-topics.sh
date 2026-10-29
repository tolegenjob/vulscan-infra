#!/bin/sh
# Запускается контейнером kafka-init (образ apache/kafka, тот же что у брокера — bin/kafka-topics.sh уже внутри).
# Идемпотентен: --if-not-exists позволяет повторный запуск (например, после docker compose restart kafka-init).
set -eu

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
PARTITIONS=3
REPLICATION=1

TOPICS="scan.jobs.xss.v1 scan.jobs.sqli.v1 scan.jobs.idor.v1 scan.job-events.v1 scan.dlq.v1"

for topic in $TOPICS; do
  echo "create-topics: $topic (partitions=$PARTITIONS, replication-factor=$REPLICATION)"
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION"
done

echo "create-topics: done, current list:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list
