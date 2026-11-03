#!/usr/bin/env bash
#
# Generates the local RS256 key pair shared by lp-auth-service and lp-gateway.
# Keys are written to infra/jwt, which is gitignored: never commit private key material.
#
# Usage: bash scripts/gen-jwt-keys.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEY_DIR="${ROOT_DIR}/infra/jwt"
PRIVATE_KEY="${KEY_DIR}/jwt-private.pem"
PUBLIC_KEY="${KEY_DIR}/jwt-public.pem"

mkdir -p "${KEY_DIR}"

if [[ -f "${PRIVATE_KEY}" || -f "${PUBLIC_KEY}" ]]; then
    echo "key files already exist in ${KEY_DIR}; remove them manually to regenerate" >&2
    exit 1
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${PRIVATE_KEY}" 2>/dev/null
openssl rsa -pubout -in "${PRIVATE_KEY}" -out "${PUBLIC_KEY}" 2>/dev/null
chmod 600 "${PRIVATE_KEY}"
chmod 644 "${PUBLIC_KEY}"

echo "generated RS256 key pair in ${KEY_DIR}"
echo "export LP_JWT_PRIVATE_KEY_PATH=\"${PRIVATE_KEY}\""
echo "export LP_JWT_PUBLIC_KEY_PATH=\"${PUBLIC_KEY}\""
