#!/bin/bash
#
# Vault Migration Script for MediaRoulette
# This script migrates secrets from .env file to HashiCorp Vault
#
# Prerequisites:
# 1. Vault server running (docker run --cap-add=IPC_LOCK -p 8200:8200 -e 'VAULT_DEV_ROOT_TOKEN_ID=myroot' vault)
# 2. Vault CLI installed (optional, this script uses REST API)
# 3. .env file with secrets to migrate
#
# Usage: ./migrate-to-vault.sh [vault-address] [vault-token] [secret-path]
#

set -e

# Default values
VAULT_ADDR="${1:-http://localhost:8200}"
VAULT_TOKEN="${2:-myroot}"
SECRET_PATH="${3:-secret/data/mediaroulette}"
ENV_FILE=".env"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "======================================"
echo "  MediaRoulette Vault Migration Tool"
echo "======================================"
echo ""

# Check if .env file exists
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}Error: .env file not found!${NC}"
    echo "Please ensure .env file exists in the current directory."
    exit 1
fi

echo -e "${YELLOW}Configuration:${NC}"
echo "  Vault Address:  $VAULT_ADDR"
echo "  Vault Token:    ${VAULT_TOKEN:0:10}..."
echo "  Secret Path:    $SECRET_PATH"
echo "  Source File:    $ENV_FILE"
echo ""

# Test Vault connection
echo -e "${YELLOW}Testing Vault connection...${NC}"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    "$VAULT_ADDR/v1/sys/health")

if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 429 ] || [ "$HTTP_CODE" -eq 473 ] || [ "$HTTP_CODE" -eq 501 ]; then
    echo -e "${GREEN}✓ Vault connection successful${NC}"
else
    echo -e "${RED}✗ Vault connection failed (HTTP $HTTP_CODE)${NC}"
    echo "Please check:"
    echo "  1. Vault is running: curl $VAULT_ADDR/v1/sys/health"
    echo "  2. Token is correct"
    echo "  3. Network connectivity"
    exit 1
fi

# Build JSON payload
echo ""
echo -e "${YELLOW}Reading secrets from .env file...${NC}"

# Initialize JSON object
JSON_DATA='{"data":{'

FIRST=true
COUNT=0

while IFS='=' read -r key value || [ -n "$key" ]; do
    # Skip comments and empty lines
    [[ $key =~ ^#.*$ ]] && continue
    [[ -z $key ]] && continue
    
    # Remove leading/trailing whitespace
    key=$(echo "$key" | xargs)
    value=$(echo "$value" | xargs)
    
    # Skip if key is empty after trimming
    [ -z "$key" ] && continue
    
    # Add comma if not first element
    if [ "$FIRST" = false ]; then
        JSON_DATA+=","
    fi
    FIRST=false
    
    # Escape quotes in value
    value=$(echo "$value" | sed 's/"/\\"/g')
    
    # Add key-value pair
    JSON_DATA+="\"$key\":\"$value\""
    
    COUNT=$((COUNT + 1))
    echo "  Found: $key"
done < "$ENV_FILE"

JSON_DATA+='}}'

echo -e "${GREEN}✓ Found $COUNT secrets to migrate${NC}"

if [ $COUNT -eq 0 ]; then
    echo -e "${YELLOW}No secrets found to migrate. Exiting.${NC}"
    exit 0
fi

# Confirm migration
echo ""
echo -e "${YELLOW}Ready to migrate $COUNT secrets to Vault.${NC}"
read -p "Continue? (y/n): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Migration cancelled.${NC}"
    exit 0
fi

# Write to Vault
echo ""
echo -e "${YELLOW}Writing secrets to Vault...${NC}"

HTTP_CODE=$(curl -s -o /tmp/vault_response.json -w "%{http_code}" \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -H "Content-Type: application/json" \
    -X POST \
    -d "$JSON_DATA" \
    "$VAULT_ADDR/v1/$SECRET_PATH")

if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 204 ]; then
    echo -e "${GREEN}✓ Successfully migrated $COUNT secrets to Vault!${NC}"
else
    echo -e "${RED}✗ Failed to write secrets to Vault (HTTP $HTTP_CODE)${NC}"
    if [ -f /tmp/vault_response.json ]; then
        echo "Response:"
        cat /tmp/vault_response.json
        echo ""
    fi
    exit 1
fi

# Verify migration
echo ""
echo -e "${YELLOW}Verifying migration...${NC}"

HTTP_CODE=$(curl -s -o /tmp/vault_read.json -w "%{http_code}" \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    "$VAULT_ADDR/v1/$SECRET_PATH")

if [ "$HTTP_CODE" -eq 200 ]; then
    STORED_COUNT=$(cat /tmp/vault_read.json | grep -o '"[^"]*":' | wc -l)
    echo -e "${GREEN}✓ Verification successful${NC}"
    echo "  Secrets in Vault: $STORED_COUNT"
else
    echo -e "${YELLOW}⚠ Could not verify secrets (HTTP $HTTP_CODE)${NC}"
fi

# Clean up temp files
rm -f /tmp/vault_response.json /tmp/vault_read.json

echo ""
echo -e "${GREEN}======================================"
echo "  Migration Complete!"
echo "======================================${NC}"
echo ""
echo "Next steps:"
echo "  1. Update vault-config.properties:"
echo "       vault.enabled=true"
echo "       vault.address=$VAULT_ADDR"
echo "       vault.token=$VAULT_TOKEN"
echo ""
echo "  2. Test the integration:"
echo "       ./gradlew run"
echo ""
echo "  3. Secure your .env file:"
echo "       mv .env .env.backup"
echo "       chmod 600 .env.backup"
echo ""
echo -e "${YELLOW}⚠ Important: Keep vault-config.properties secure!${NC}"
echo "  Never commit it to version control."
echo "  Add it to .gitignore if not already present."
echo ""
