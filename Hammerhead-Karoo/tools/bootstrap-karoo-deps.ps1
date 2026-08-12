# Clone karoo-ext for composite Gradle build (gitignored under .deps/).
param(
    [string]$Ref = "master"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root ".deps\karoo-ext"

if (Test-Path (Join-Path $dest ".git")) {
    Write-Host "karoo-ext already present at $dest"
    exit 0
}

New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
git clone --depth 1 --branch $Ref https://github.com/hammerheadnav/karoo-ext.git $dest
Write-Host "Cloned karoo-ext to $dest"
