param(
    [ValidateSet("exe", "app-image")]
    [string]$Type = "exe"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot

Push-Location $projectRoot
try {
    [xml]$pom = Get-Content -Raw "pom.xml"
    $appVersion = $pom.project.version
    $mainJar = "echosoul-$appVersion.jar"

    Write-Host "[INFO] Building EchoSoul with Maven and jlink..."
    mvn -B -DskipTests clean package

    $jpackageArgs = @(
        "--type", $Type,
        "--dest", "target/installer",
        "--input", "target/jpackage-input",
        "--name", "EchoSoul",
        "--app-version", $appVersion,
        "--main-class", "app.Main",
        "--main-jar", $mainJar,
        "--runtime-image", "target/runtime/echosoul-jre"
    )

    $iconCandidates = @(
        (Join-Path $projectRoot "resources\\images\\EchoSoul.ico"),
        (Join-Path $projectRoot "EchoSoul.ico")
    )
    $iconPath = $iconCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($iconPath) {
        $jpackageArgs += @("--icon", $iconPath)
    } else {
        Write-Warning "EchoSoul.ico was not found in resources/images or the project root. jpackage will use its default icon."
    }

    if ($Type -eq "exe") {
        $jpackageArgs += @("--win-dir-chooser", "--win-shortcut")
    }

    Write-Host "[INFO] Running jpackage..."
    & jpackage @jpackageArgs
}
finally {
    Pop-Location
}
