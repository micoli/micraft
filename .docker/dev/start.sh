#!/bin/bash
set -e
cd /workspace/app/webApp/ts-src && npm install
cd /workspace
echo "Starting pitchfork"
pitchfork supervisor run --container --boot --force
