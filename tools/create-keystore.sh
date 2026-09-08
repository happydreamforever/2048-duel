#!/usr/bin/env bash
# Creates the release signing key once. Usage: tools/create-keystore.sh [password]
set -e
cd "$(dirname "$0")/.."
KS=android/keystore/duel2048-release.jks
if [ -f "$KS" ]; then echo "keystore already exists: $KS"; exit 0; fi
PASS="${1:-$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)}"
mkdir -p android/keystore
keytool -genkeypair -v -keystore "$KS" -alias duel2048 -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=2048 Duel, OU=Dev, O=Duel2048, C=US"
cat > keystore.properties <<PROPS
KEYSTORE_FILE=$KS
KEYSTORE_PASSWORD=$PASS
KEY_ALIAS=duel2048
KEY_PASSWORD=$PASS
PROPS
echo "Created $KS and keystore.properties. Keep both private and backed up: every future update must be signed with this key."
