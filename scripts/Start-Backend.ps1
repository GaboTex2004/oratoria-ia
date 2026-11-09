$ErrorActionPreference = 'Continue'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$env:JAVA_HOME -and (Test-Path 'C:/Program Files/Java/jdk-21')) { $env:JAVA_HOME = 'C:/Program Files/Java/jdk-21' }
$javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\mvnw.cmd -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el backend. Se requiere Java 21.' }
    # A global DEBUG value used by other tools must not enable request logging here.
    & $javaExecutable -jar target/backend-0.0.1-SNAPSHOT.jar --debug=false --logging.level.root=INFO --logging.level.org.springframework=INFO
} finally { Pop-Location }
