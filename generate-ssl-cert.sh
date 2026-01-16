#!/bin/bash
# Generate self-signed SSL certificates for development/testing
# For production, use Let's Encrypt or a real CA

set -e

echo "=========================================="
echo "  SSL Certificate Generator"
echo "=========================================="

# Generate certificates for Nginx (Docker)
NGINX_CERT_DIR="nginx/certs"
mkdir -p "$NGINX_CERT_DIR"

echo ""
echo "Generating certificates for Nginx (Docker)..."

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "$NGINX_CERT_DIR/server.key" \
    -out "$NGINX_CERT_DIR/server.crt" \
    -subj "/CN=localhost/O=FileUpload/C=US" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "✅ Nginx certificates generated:"
echo "   - $NGINX_CERT_DIR/server.crt"
echo "   - $NGINX_CERT_DIR/server.key"

# Generate keystore for Spring Boot (local development)
KEYSTORE_FILE="src/main/resources/keystore.p12"
KEYSTORE_PASSWORD="changeit"
KEY_ALIAS="fileupload"

echo ""
echo "Generating keystore for Spring Boot (local dev)..."

keytool -genkeypair \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -storetype PKCS12 \
    -keystore "$KEYSTORE_FILE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -validity 365 \
    -dname "CN=localhost, OU=Development, O=FileUpload, L=City, ST=State, C=US" \
    -ext "SAN=dns:localhost,ip:127.0.0.1" \
    2>/dev/null || true

echo "✅ Spring Boot keystore generated:"
echo "   - $KEYSTORE_FILE"

echo ""
echo "=========================================="
echo "  Setup Complete!"
echo "=========================================="
echo ""
echo "🐳 For Docker (recommended):"
echo "   docker-compose up --build"
echo "   Access: https://localhost"
echo ""
echo "💻 For local development:"
echo "   export SSL_ENABLED=true"
echo "   export SSL_KEYSTORE_PASSWORD=changeit"
echo "   ./mvnw spring-boot:run (or run from IDE)"
echo "   Access: https://localhost:8080"
echo ""
echo "⚠️  Browser will show security warning for self-signed certs."
echo "   This is expected for development. Click 'Advanced' → 'Proceed'."
echo ""
