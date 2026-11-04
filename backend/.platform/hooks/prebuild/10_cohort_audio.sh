#!/usr/bin/env bash
set -euo pipefail

# Kept outside /var/app/current so application deployments retain recordings.
# Instance replacement still requires a durable shared volume or restored backup.
install -d -m 0700 -o webapp -g webapp /var/app/manabihub-storage/cohort-audio

if [[ ! -f tools/ffmpeg/ffmpeg ]]; then
  echo 'The deployment bundle is missing the Linux audio decoder.' >&2
  exit 1
fi
chmod 0755 tools/ffmpeg/ffmpeg
