#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../../.."

grep -q '<java.version>25</java.version>' pom.xml
grep -q 'java = "25"' .mise.toml
grep -q "java-version: '25'" .github/workflows/ci.yml
grep -q 'maven:3.9-eclipse-temurin-25' Dockerfile
grep -q 'eclipse-temurin:25-jre' Dockerfile
grep -q '<org.projectlombok.version>1.18.46</org.projectlombok.version>' pom.xml
grep -q 'runtime: docker' render.yaml
grep -q 'dockerfilePath: ./Dockerfile' render.yaml
grep -q 'JAVA_TOOL_OPTIONS' render.yaml docker-compose.yml
grep -q 'USER appuser' Dockerfile
grep -q 'Java-25-orange' readme.md
grep -q 'health/readiness' render.yaml

! grep -q 'temurin-21' Dockerfile
! grep -q 'runtime: java' render.yaml
! grep -q 'JAVA_OPTS' render.yaml docker-compose.yml
! grep -q 'java.version>21' pom.xml
! grep -q '1.18.36' pom.xml

echo "java25-touchpoints-ok"
