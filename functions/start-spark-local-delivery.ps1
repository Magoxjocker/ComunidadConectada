[CmdletBinding(DefaultParameterSetName = 'Start')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Configure')]
    [switch]$Configure
)

$ErrorActionPreference = 'Stop'
$settingsDirectory = Join-Path $env:LOCALAPPDATA 'ComunidadConectada\Delivery'
$configurationFile = Join-Path $settingsDirectory 'configuration.json'
$credentialFile = Join-Path $settingsDirectory 'mqtt-credential.xml'
$logFile = Join-Path $settingsDirectory 'delivery.log'

if ($Configure) {
    $brokerHost = Read-Host 'Host MQTT y puerto TLS (por ejemplo 192.168.18.9:8883)'
    $publisher = Read-Host 'Usuario MQTT publicador'
    $password = Read-Host 'Contraseña MQTT publicador' -AsSecureString
    $certificatePath = Read-Host 'Ruta absoluta de la CA MQTT (Enter usa la confianza de Node.js)'
    $firebaseKeyPath = Read-Host 'Ruta absoluta de credenciales ADC (Enter usa ADC predeterminado)'
    $announcementsSince = Read-Host 'Fecha UTC fija para habilitar FCM (Enter deja solo SOS)'
    if ([string]::IsNullOrWhiteSpace($brokerHost) -or [string]::IsNullOrWhiteSpace($publisher) -or $password.Length -eq 0) {
        throw 'Indica el host, el usuario y la contraseña MQTT.'
    }
    New-Item -ItemType Directory -Path $settingsDirectory -Force | Out-Null
    [pscredential]::new($publisher, $password) | Export-Clixml -LiteralPath $credentialFile
    [ordered]@{
        CC_PROJECT_ID = 'comunidadconectada-35c3c'
        CC_DATABASE_URL = 'https://comunidadconectada-35c3c-default-rtdb.firebaseio.com'
        CC_MQTT_HOST = $brokerHost.Trim()
        NODE_EXTRA_CA_CERTS = $certificatePath.Trim()
        GOOGLE_APPLICATION_CREDENTIALS = $firebaseKeyPath.Trim()
        CC_ANNOUNCEMENTS_SINCE = $announcementsSince.Trim()
    } | ConvertTo-Json | Set-Content -LiteralPath $configurationFile -Encoding UTF8
    Write-Output "Configuración local guardada en $settingsDirectory"
    return
}

if (!(Test-Path -LiteralPath $configurationFile) -or !(Test-Path -LiteralPath $credentialFile)) {
    throw 'Ejecuta este script con -Configure bajo tu usuario de Windows.'
}
$configuration = Get-Content -LiteralPath $configurationFile -Raw -Encoding UTF8 | ConvertFrom-Json
$mqttCredential = Import-Clixml -LiteralPath $credentialFile
$environmentNames = @(
    'CC_PROJECT_ID', 'CC_DATABASE_URL', 'CC_MQTT_HOST',
    'NODE_EXTRA_CA_CERTS', 'GOOGLE_APPLICATION_CREDENTIALS', 'CC_ANNOUNCEMENTS_SINCE'
)
foreach ($pathName in @('NODE_EXTRA_CA_CERTS', 'GOOGLE_APPLICATION_CREDENTIALS')) {
    $configuredPath = $configuration.$pathName
    if ($configuredPath -and (!( [System.IO.Path]::IsPathRooted($configuredPath)) -or
            !(Test-Path -LiteralPath $configuredPath -PathType Leaf))) {
        throw "Ruta inválida para $pathName."
    }
}

$windowsBroker = Get-Service -Name 'mosquitto' -ErrorAction SilentlyContinue
$brokerState = if ($windowsBroker) { $windowsBroker.Status } else { 'Sin servicio local registrado' }
$startupMessage = '{0} Mosquitto en Windows: {1}. Node.js comprueba el endpoint TLS.' -f (Get-Date -Format o), $brokerState
$startupMessage | Tee-Object -FilePath $logFile -Append
$node = Get-Command node.exe -CommandType Application
$entryPoint = Join-Path $PSScriptRoot 'spark-local-delivery.js'

try {
    foreach ($environmentName in $environmentNames) {
        [Environment]::SetEnvironmentVariable($environmentName, $configuration.$environmentName, 'Process')
    }
    $env:CC_MQTT_PUBLISHER_USERNAME = $mqttCredential.UserName
    $env:CC_MQTT_PUBLISHER_PASSWORD = $mqttCredential.GetNetworkCredential().Password
    & $node.Source $entryPoint | Tee-Object -FilePath $logFile -Append
    $deliveryExitCode = $LASTEXITCODE
} finally {
    Remove-Item -LiteralPath Env:CC_MQTT_PUBLISHER_PASSWORD, Env:CC_MQTT_PUBLISHER_USERNAME -ErrorAction SilentlyContinue
}
exit $deliveryExitCode
