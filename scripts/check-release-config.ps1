$ErrorActionPreference = 'Stop'

$requiredVariables = @(
    'SPRING_PROFILES_ACTIVE',
    'SPRING_DATASOURCE_URL',
    'RDS_USERNAME',
    'RDS_PASSWORD',
    'GOOGLE_CLIENT_ID',
    'GOOGLE_CLIENT_SECRET',
    'JWT_SECRET',
    'KYC_IDENTITY_SECRET',
    'PAYOUT_SECURITY_SECRET',
    'MAIL_USERNAME',
    'MAIL_PASSWORD',
    'VNPAY_TMN_CODE',
    'VNPAY_HASH_SECRET',
    'VNPAY_RETURN_URL',
    'AI_CHAT_PROVIDER_BASE_URL',
    'AI_CHAT_PROVIDER_API_KEY',
    'AI_CHAT_PROVIDER_MODEL',
    'FRONTEND_BASE_URL',
    'CORS_ALLOWED_ORIGINS',
    'PHONE_VERIFICATION_SMS_MODE',
    'WITHDRAWAL_OTP_MODE'
)

$profile = [Environment]::GetEnvironmentVariable('SPRING_PROFILES_ACTIVE')
if ($profile -ne 'prod') {
    Write-Error 'Release configuration is not ready. SPRING_PROFILES_ACTIVE must be prod.'
}

$smsMode = ([string][Environment]::GetEnvironmentVariable('PHONE_VERIFICATION_SMS_MODE')).Trim().ToLowerInvariant()
if ($smsMode -eq 'esms') {
    $requiredVariables += @(
        'PHONE_VERIFICATION_ESMS_API_KEY',
        'PHONE_VERIFICATION_ESMS_SECRET_KEY',
        'PHONE_VERIFICATION_ESMS_BRANDNAME',
        'PHONE_VERIFICATION_ESMS_SANDBOX'
    )
} elseif ($smsMode -eq 'webhook') {
    $requiredVariables += @(
        'PHONE_VERIFICATION_SMS_WEBHOOK_URL',
        'PHONE_VERIFICATION_SMS_API_KEY'
    )
} elseif ($smsMode -eq 'firebase') {
    $requiredVariables += @(
        'FIREBASE_PHONE_AUTH_ENABLED',
        'FIREBASE_PROJECT_ID',
        'FIREBASE_SERVICE_ACCOUNT_JSON_BASE64'
    )
} else {
    Write-Error 'Release configuration is not ready. PHONE_VERIFICATION_SMS_MODE must be firebase, esms, or webhook.'
}

$withdrawalOtpMode = ([string][Environment]::GetEnvironmentVariable('WITHDRAWAL_OTP_MODE')).Trim().ToLowerInvariant()
if ($withdrawalOtpMode -eq 'firebase') {
    $requiredVariables += @(
        'FIREBASE_PHONE_AUTH_ENABLED',
        'FIREBASE_PROJECT_ID',
        'FIREBASE_SERVICE_ACCOUNT_JSON_BASE64'
    )
} elseif ($withdrawalOtpMode -ne 'email') {
    Write-Error 'Release configuration is not ready. WITHDRAWAL_OTP_MODE must be firebase or email.'
}

$requiredVariables = @($requiredVariables | Select-Object -Unique)

$minimumLengthVariables = @{
    JWT_SECRET = 32
    KYC_IDENTITY_SECRET = 32
    PAYOUT_SECURITY_SECRET = 32
    FIREBASE_PROJECT_ID = 6
    FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 = 200
}

$states = foreach ($name in $requiredVariables) {
    $value = [Environment]::GetEnvironmentVariable($name)
    $length = if ($null -eq $value) { 0 } else { $value.Length }
    $minimumLength = if ($minimumLengthVariables.ContainsKey($name)) {
        $minimumLengthVariables[$name]
    } else {
        1
    }

    [PSCustomObject]@{
        Name = $name
        Configured = -not [string]::IsNullOrWhiteSpace($value)
        CharacterCount = $length
        MinimumLength = $minimumLength
        ValidLength = $length -ge $minimumLength
    }
}

$states | Format-Table -AutoSize

$invalid = @(
    $states | Where-Object {
        -not $_.Configured -or -not $_.ValidLength
    }
)

$placeholderPattern = '^(<.*>|replace-with.*|changeme|your[-_].*)$'
$placeholderProviderVariables = @(
    $states | Where-Object {
        ($_.Name -like 'PHONE_VERIFICATION_*' -or $_.Name -like 'FIREBASE_*') -and
        [Environment]::GetEnvironmentVariable($_.Name) -match $placeholderPattern
    }
)

if ($smsMode -eq 'esms') {
    $sandbox = [Environment]::GetEnvironmentVariable('PHONE_VERIFICATION_ESMS_SANDBOX')
    if ($sandbox -ne '0') {
        Write-Error 'Release configuration is not ready. PHONE_VERIFICATION_ESMS_SANDBOX must be 0 for real SMS delivery.'
    }
}

if ($smsMode -eq 'firebase' -or $withdrawalOtpMode -eq 'firebase') {
    $firebaseEnabled = [Environment]::GetEnvironmentVariable('FIREBASE_PHONE_AUTH_ENABLED')
    if ($firebaseEnabled -ne 'true') {
        Write-Error 'Release configuration is not ready. FIREBASE_PHONE_AUTH_ENABLED must be true.'
    }
    try {
        $firebaseJsonBytes = [Convert]::FromBase64String(
            [Environment]::GetEnvironmentVariable('FIREBASE_SERVICE_ACCOUNT_JSON_BASE64'))
        $firebaseCredentials = [Text.Encoding]::UTF8.GetString($firebaseJsonBytes) | ConvertFrom-Json
        $firebaseProjectId = [Environment]::GetEnvironmentVariable('FIREBASE_PROJECT_ID')
        if ($firebaseCredentials.type -ne 'service_account' -or
            [string]::IsNullOrWhiteSpace($firebaseCredentials.private_key) -or
            [string]::IsNullOrWhiteSpace($firebaseCredentials.client_email) -or
            $firebaseCredentials.project_id -ne $firebaseProjectId) {
            Write-Error 'Release configuration is not ready. Firebase service-account JSON is invalid or belongs to another project.'
        }
    } catch {
        Write-Error 'Release configuration is not ready. FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 is not valid Base64 service-account JSON.'
    } finally {
        Remove-Variable firebaseJsonBytes, firebaseCredentials -ErrorAction SilentlyContinue
    }
}

if ($invalid.Count -gt 0) {
    $errorMessage = 'Release configuration is not ready. Missing or too-short variables: ' +
        ($invalid.Name -join ', ')
    Write-Error $errorMessage
}

if ($placeholderProviderVariables.Count -gt 0) {
    Write-Error ('Release configuration is not ready. Placeholder provider variables: ' +
        ($placeholderProviderVariables.Name -join ', '))
}

Write-Host 'Release configuration is present. Values were intentionally not printed.'
