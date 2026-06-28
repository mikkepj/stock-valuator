$env:JAVA_HOME = "C:/DevTools/java/jdk-21.0.10"
$result = & mvn test -q 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "BLOCKED: mvn test fallo. Corrige los tests antes de hacer commit."
    exit 1
}
exit 0
