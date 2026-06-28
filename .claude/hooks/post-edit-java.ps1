param([string]$FilePath)
if ($FilePath -match "\.java$") {
    $env:JAVA_HOME = "C:/DevTools/java/jdk-21.0.10"
    & mvn checkstyle:check -q 2>&1 | Select-String -Pattern "ERROR|WARNING" | Select-Object -First 10
}
exit 0
