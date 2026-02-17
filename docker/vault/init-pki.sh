#!/bin/sh
set -e

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=dev-root-token

echo "=== Enabling PKI Root CA ==="
vault secrets enable -path=pki-root pki
vault secrets tune -max-lease-ttl=87600h pki-root

vault write -format=json pki-root/root/generate/internal \
    common_name="Tawqi Root CA" \
    ttl=87600h \
    key_bits=4096 > /tmp/root-ca.json

echo "=== Enabling PKI Intermediate CA ==="
vault secrets enable -path=pki-campus pki
vault secrets tune -max-lease-ttl=43800h pki-campus

vault write -format=json pki-campus/intermediate/generate/internal \
    common_name="Tawqi Campus Intermediate CA" \
    key_bits=4096 > /tmp/intermediate-csr.json

# Extract CSR and sign with root
CSR=$(cat /tmp/intermediate-csr.json | sed -n 's/.*"csr": *"\(.*\)".*/\1/p' | sed 's/\\n/\n/g')
echo "$CSR" > /tmp/intermediate.csr

vault write -format=json pki-root/root/sign-intermediate \
    csr=@/tmp/intermediate.csr \
    format=pem_bundle \
    ttl=43800h > /tmp/intermediate-cert.json

CERT=$(cat /tmp/intermediate-cert.json | sed -n 's/.*"certificate": *"\(.*\)".*/\1/p' | sed 's/\\n/\n/g')
echo "$CERT" > /tmp/intermediate.pem

vault write pki-campus/intermediate/set-signed \
    certificate=@/tmp/intermediate.pem

echo "=== Configuring CA URLs ==="
vault write pki-campus/config/urls \
    issuing_certificates="http://127.0.0.1:8200/v1/pki-campus/ca" \
    crl_distribution_points="http://127.0.0.1:8200/v1/pki-campus/crl"

echo "=== Creating signer-cert role ==="
vault write pki-campus/roles/signer-cert \
    allowed_domains="tawqi.local" \
    allow_subdomains=true \
    allow_any_name=true \
    max_ttl=8760h \
    key_type=rsa \
    key_bits=2048 \
    key_usage="DigitalSignature" \
    ext_key_usage="CodeSigning" \
    no_store=false \
    generate_lease=true

echo "=== Enabling Transit engine ==="
vault secrets enable transit

vault write transit/keys/signer-test-001 \
    type=rsa-2048

echo "=== Enabling KV v2 ==="
vault secrets enable -version=2 -path=secret kv

echo "=== PKI bootstrap complete ==="
vault read pki-campus/roles/signer-cert
vault read transit/keys/signer-test-001
