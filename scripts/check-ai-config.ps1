$ErrorActionPreference = 'Stop'

$requiredVariables = @(
    'AI_CHAT_PROVIDER_BASE_URL',
    'AI_CHAT_PROVIDER_API_KEY',
    'AI_CHAT_PROVIDER_MODEL'
)

$optionalVariables = @(
    'AI_CHAT_PROVIDER_ENDPOINT',
    'AI_CHAT_RATE_LIMIT_PER_MINUTE',
    'AI_CHAT_DAILY_LIMIT',
    'AI_CHAT_TIMEOUT_SECONDS'
)

function Get-SafeConfigurationState {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [bool]$Required
    )

    $value = [Environment]::GetEnvironmentVariable($Name)
    [PSCustomObject]@{
        Name = $Name
        Required = $Required
        Configured = -not [string]::IsNullOrWhiteSpace($value)
        CharacterCount = if ($null -eq $value) { 0 } else { $value.Length }
    }
}

$states = @(
    $requiredVariables | ForEach-Object {
        Get-SafeConfigurationState -Name $_ -Required $true
    }
    $optionalVariables | ForEach-Object {
        Get-SafeConfigurationState -Name $_ -Required $false
    }
)

$states | Format-Table -AutoSize

$missingRequired = @($states | Where-Object { $_.Required -and -not $_.Configured })
if ($missingRequired.Count -gt 0) {
    Write-Error "AI provider is not ready. Missing required variables: $($missingRequired.Name -join ', ')"
}

Write-Host 'AI provider configuration is present. Values were intentionally not printed.'
