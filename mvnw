#!/bin/sh
# Maven Wrapper script for Unix-like systems
# Downloads Maven if not present, then runs it

set -e

MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven-3.9.9"
MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"

if [ ! -d "${MAVEN_HOME}" ]; then
    echo "Downloading Maven 3.9.9..."
    mkdir -p "${MAVEN_HOME}"
    TMP_FILE=$(mktemp /tmp/maven-XXXXXX.zip)
    if command -v curl > /dev/null 2>&1; then
        curl -fsSL -o "${TMP_FILE}" "${MAVEN_URL}"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "${TMP_FILE}" "${MAVEN_URL}"
    else
        echo "Error: curl or wget required" >&2
        exit 1
    fi
    unzip -q -o "${TMP_FILE}" -d "${MAVEN_HOME}"
    rm -f "${TMP_FILE}"
fi

MAVEN_BIN=$(find "${MAVEN_HOME}" -name mvn -type f | head -1)
exec "${MAVEN_BIN}" "$@"
