#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ -f Gemfile ]] && command -v bundle >/dev/null 2>&1; then
  exec bundle exec jekyll serve --baseurl="" "$@"
fi

if ! command -v jekyll >/dev/null 2>&1; then
  echo "jekyll is not installed or is not on PATH" >&2
  echo "Install it with: gem install jekyll bundler" >&2
  exit 1
fi

exec jekyll serve --baseurl="" "$@"
