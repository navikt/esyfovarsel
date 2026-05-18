#!/usr/bin/env bash

set -euo pipefail

readonly json_file="${1:-}"
readonly message_key="${2:-${KAFKA_MESSAGE_KEY:-local-key}}"
readonly topic="${3:-${KAFKA_TOPIC:-team-esyfo.varselbus}}"
readonly docker_container="${KAFKA_DOCKER_CONTAINER:-broker}"
readonly bootstrap_server="${KAFKA_BOOTSTRAP_SERVER:-broker:29092}"
readonly key_separator="${KAFKA_KEY_SEPARATOR:-@@@}"

usage() {
  echo "Bruk: $0 <path-til-json-fil> [message-key] [topic]" >&2
  echo "Overstyr ved behov med KAFKA_DOCKER_CONTAINER, KAFKA_BOOTSTRAP_SERVER, KAFKA_TOPIC, KAFKA_MESSAGE_KEY eller KAFKA_KEY_SEPARATOR." >&2
}

if [[ -z "$json_file" ]]; then
  echo "Mangler path til JSON-fil." >&2
  usage
  exit 1
fi

if [[ ! -f "$json_file" ]]; then
  echo "Fant ikke JSON-fil: $json_file" >&2
  exit 1
fi

if [[ ! -s "$json_file" ]]; then
  echo "JSON-filen er tom: $json_file" >&2
  exit 1
fi

json_payload="$(tr -d '\r\n' < "$json_file")"

printf '%s%s%s\n' "$message_key" "$key_separator" "$json_payload" | \
  docker exec -i "$docker_container" kafka-console-producer \
    --bootstrap-server "$bootstrap_server" \
    --topic "$topic" \
    --property parse.key=true \
    --property "key.separator=$key_separator"
