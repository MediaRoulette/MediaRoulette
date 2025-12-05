# Vault Migration Script for MediaRoulette (PowerShell)
# This script migrates secrets from .env file to HashiCorp Vault
#
# Prerequisites:
# 1. Vault server running
# 2. .env file with secrets to migrate
#
# Usage: .\migrate-to-vault.ps1 [-VaultAddr "http://localhost:8200"] [-VaultToken "myroot"] [-SecretPath "secret/data/mediaroulette"]
#

param(
    [string]$VaultAddr = "http://localhost:8200",
    [string]$VaultToken = "myroot",
    [string]$SecretPath = "secret/data/mediaroulette",
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  MediaRoulette Vault Migration Tool" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Check if .env file exists
if (-not (Test-Path $EnvFile)) {
    Write-Host "Error: .env file not found!" -ForegroundColor Red
    Write-Host "Please ensure .env file exists in the current directory."
    exit 1
}

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Vault Address:  $VaultAddr"
Write-Host "  Vault Token:    $($VaultToken.Substring(0, [Math]::Min(10, $VaultToken.Length)))..."
Write-Host "  Secret Path:    $SecretPath"
Write-Host "  Source File:    $EnvFile"
Write-Host ""

# Test Vault connection
Write-Host "Testing Vault connection..." -ForegroundColor Yellow
try {
    $headers = @{
        "X-Vault-Token" = $VaultToken
    }
    $response = Invoke-WebRequest -Uri "$VaultAddr/v1/sys/health" -Headers $headers -Method Get -UseBasicParsing
    Write-Host "✓ Vault connection successful" -ForegroundColor Green
} catch {
    Write-Host "✗ Vault connection failed" -ForegroundColor Red
    Write-Host "Please check:" -ForegroundColor Yellow
    Write-Host "  1. Vault is running"
    Write-Host "  2. Token is correct"
    Write-Host "  3. Network connectivity"
    exit 1
}

# Read secrets from .env file
Write-Host ""
Write-Host "Reading secrets from .env file..." -ForegroundColor Yellow

$secrets = @{}
$count = 0

Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    
    # Skip comments and empty lines
    if ($line -match '^#' -or $line -eq '') {
        return
    }
    
    # Parse key=value
    if ($line -match '^([^=]+)=(.*)$') {
        $key = $matches[1].Trim()
        $value = $matches[2].Trim()
        
        if ($key -ne '') {
            $secrets[$key] = $value
            $count++
            Write-Host "  Found: $key"
        }
    }
}

Write-Host "✓ Found $count secrets to migrate" -ForegroundColor Green

if ($count -eq 0) {
    Write-Host "No secrets found to migrate. Exiting." -ForegroundColor Yellow
    exit 0
}

# Confirm migration
Write-Host ""
Write-Host "Ready to migrate $count secrets to Vault." -ForegroundColor Yellow
$confirmation = Read-Host "Continue? (y/n)"

if ($confirmation -ne 'y' -and $confirmation -ne 'Y') {
    Write-Host "Migration cancelled." -ForegroundColor Yellow
    exit 0
}

# Build JSON payload
$jsonData = @{
    data = $secrets
} | ConvertTo-Json -Depth 10

# Write to Vault
Write-Host ""
Write-Host "Writing secrets to Vault..." -ForegroundColor Yellow

try {
    $headers = @{
        "X-Vault-Token" = $VaultToken
        "Content-Type" = "application/json"
    }
    
    $response = Invoke-WebRequest -Uri "$VaultAddr/v1/$SecretPath" `
        -Headers $headers `
        -Method Post `
        -Body $jsonData `
        -UseBasicParsing
    
    Write-Host "✓ Successfully migrated $count secrets to Vault!" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to write secrets to Vault" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Verify migration
Write-Host ""
Write-Host "Verifying migration..." -ForegroundColor Yellow

try {
    $headers = @{
        "X-Vault-Token" = $VaultToken
    }
    
    $response = Invoke-WebRequest -Uri "$VaultAddr/v1/$SecretPath" `
        -Headers $headers `
        -Method Get `
        -UseBasicParsing
    
    $data = ($response.Content | ConvertFrom-Json).data.data
    $storedCount = ($data.PSObject.Properties | Measure-Object).Count
    
    Write-Host "✓ Verification successful" -ForegroundColor Green
    Write-Host "  Secrets in Vault: $storedCount"
} catch {
    Write-Host "⚠ Could not verify secrets" -ForegroundColor Yellow
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host "  Migration Complete!" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Update vault-config.properties:"
Write-Host "       vault.enabled=true"
Write-Host "       vault.address=$VaultAddr"
Write-Host "       vault.token=$VaultToken"
Write-Host ""
Write-Host "  2. Test the integration:"
Write-Host "       .\gradlew.bat run"
Write-Host ""
Write-Host "  3. Secure your .env file:"
Write-Host "       Rename-Item .env .env.backup"
Write-Host ""
Write-Host "⚠ Important: Keep vault-config.properties secure!" -ForegroundColor Yellow
Write-Host "  Never commit it to version control."
Write-Host "  Add it to .gitignore if not already present."
Write-Host ""
