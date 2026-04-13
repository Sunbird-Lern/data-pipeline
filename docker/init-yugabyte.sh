#!/bin/bash
# Downloads CQL migration scripts from sunbird-spark-installer and runs them
# against the local YugabyteDB container.
#
# Usage: ./init-yugabyte.sh [ENVIRONMENT] [BRANCH]
#   ENVIRONMENT: keyspace prefix (default: dev)
#   BRANCH:      branch of sunbird-spark-installer to use (default: develop)
#
# Prerequisites: docker must be running with the yugabyte container up.

set -e

ENV="${1:-dev}"
BRANCH="${2:-develop}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="${SCRIPT_DIR}/.migrations"
REPO_URL="https://github.com/Sunbird-Spark/sunbird-spark-installer.git"
REPO_PATH="scripts/sunbird-yugabyte-migrations/sunbird-lern"

CQL_FILES=(
    "sunbird.cql"
    "sunbird_courses.cql"
    "sunbird_groups.cql"
    "sunbird_notifications.cql"
    "sunbird_programs.cql"
    "qmzbm_form_service.cql"
)

echo "Downloading CQL migration scripts (branch: ${BRANCH})..."
rm -rf "${MIGRATIONS_DIR}"
if ! git clone --depth 1 --branch "${BRANCH}" --filter=blob:none --sparse "${REPO_URL}" "${MIGRATIONS_DIR}"; then
    echo "ERROR: Failed to clone ${REPO_URL} (branch: ${BRANCH})"
    exit 1
fi
cd "${MIGRATIONS_DIR}"
if ! git sparse-checkout set "${REPO_PATH}"; then
    echo "ERROR: Failed to sparse-checkout ${REPO_PATH}"
    exit 1
fi
cd "${SCRIPT_DIR}"

echo "Running migrations with ENV=${ENV}..."

FAILED=0
for cql_file in "${CQL_FILES[@]}"; do
    src="${MIGRATIONS_DIR}/${REPO_PATH}/${cql_file}"
    if [ ! -f "${src}" ]; then
        echo "SKIP: ${cql_file} not found"
        continue
    fi

    tmp="/tmp/lern_${cql_file}"
    sed "s/\${ENV}/${ENV}/g" "${src}" > "${tmp}"

    docker cp "${tmp}" yugabyte:/tmp/"${cql_file}"
    if docker exec yugabyte /home/yugabyte/bin/ycqlsh 127.0.0.1 9042 \
        -u cassandra -p cassandra \
        -f /tmp/"${cql_file}" 2>&1; then
        echo "OK: ${cql_file}"
    else
        echo "FAIL: ${cql_file}"
        FAILED=$((FAILED + 1))
    fi
    rm -f "${tmp}"
done

rm -rf "${MIGRATIONS_DIR}"

echo ""
if [ ${FAILED} -gt 0 ]; then
    echo "${FAILED} migration(s) failed."
    exit 1
else
    echo "All migrations completed successfully."
fi
