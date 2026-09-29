#!/bin/bash

if [[ $# -gt 0 ]]; then
 EXPORT_IMAGES=1 ./gradlew :dgir:test --rerun-tasks 2>&1 | grep -E "FAILED|SKIPPED|BUILD|tests completed|Fehler|STANDARD_ERROR"
else
 ./gradlew :dgir:test --rerun-tasks 2>&1 | grep -E "FAILED|SKIPPED|BUILD|tests completed|Fehler|STANDARD_ERROR"
fi
