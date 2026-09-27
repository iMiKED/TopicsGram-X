[CmdletBinding()]
param([string] $ImageMagick = 'magick')

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$source = Join-Path $PSScriptRoot 'topics-tabs-large-source.png'
$mainResources = Join-Path $repository 'app/src/main/res'
$adaptiveResources = Join-Path $repository 'app/src/sinceOreo/res'
$outputDirectory = Join-Path $repository 'app/build/tmp/topicsgram-icons'
[void][IO.Directory]::CreateDirectory($outputDirectory)

function Invoke-ImageMagick([string[]] $ImageArguments) {
    & $ImageMagick @ImageArguments
    if ($LASTEXITCODE -ne 0) { throw "ImageMagick failed with exit code $LASTEXITCODE" }
}

# Only add the generated tabs. Upstream airplane and background assets stay intact.
$adaptiveSizes = [ordered]@{ldpi=81; mdpi=108; hdpi=162; xhdpi=216; xxhdpi=324; xxxhdpi=432}
foreach ($density in $adaptiveSizes.Keys) {
    $size = $adaptiveSizes[$density]
    $directory = Join-Path $adaptiveResources "mipmap-$density-v26"
    [void][IO.Directory]::CreateDirectory($directory)
    Invoke-ImageMagick @($source, '-resize', "${size}x${size}", '-strip',
        (Join-Path $directory 'topicsgram_tabs_round.png'))
    $insetSize = [int][Math]::Round($size * 0.87)
    Invoke-ImageMagick @($source, '-resize', "${insetSize}x${insetSize}",
        '-gravity', 'center', '-background', 'none', '-extent', "${size}x${size}", '-strip',
        (Join-Path $directory 'topicsgram_tabs.png'))
}

# The legacy plane is the round adaptive foreground at 5/8 scale, center-cropped.
# DstOut hides tabs beneath that plane before compositing on the original icon,
# preserving the existing background, circular border and airplane pixels.
$legacyTabs = Join-Path $outputDirectory 'legacy-tabs.png'
$legacyPlane = Join-Path $outputDirectory 'legacy-plane.png'
$visibleTabs = Join-Path $outputDirectory 'legacy-visible-tabs.png'
Invoke-ImageMagick @($source, '-resize', '270x270', '-gravity', 'center',
    '-background', 'none', '-extent', '192x192', $legacyTabs)
Invoke-ImageMagick @((Join-Path $adaptiveResources 'mipmap-xxxhdpi-v26/app_adaptive_fg_round.webp'),
    '-resize', '270x270', '-gravity', 'center', '-background', 'none', '-extent', '192x192', $legacyPlane)
Invoke-ImageMagick @($legacyTabs, $legacyPlane, '-compose', 'DstOut', '-composite', $visibleTabs)

$legacySizes = [ordered]@{mdpi=48; hdpi=72; xhdpi=96; xxhdpi=144; xxxhdpi=192}
foreach ($density in $legacySizes.Keys) {
    $size = $legacySizes[$density]
    $directory = Join-Path $mainResources "mipmap-$density"
    $tabsForDensity = Join-Path $outputDirectory "tabs-$density.png"
    Invoke-ImageMagick @($visibleTabs, '-resize', "${size}x${size}", $tabsForDensity)
    foreach ($suffix in @('', '_round')) {
        Invoke-ImageMagick @((Join-Path $directory "app_launcher$suffix.png"), $tabsForDensity,
            '-compose', 'Over', '-composite', '-strip', (Join-Path $directory "topicsgram_launcher$suffix.png"))
    }
}

$preview = Join-Path $PSScriptRoot 'topicsgram-icon-preview.png'
Invoke-ImageMagick @((Join-Path $adaptiveResources 'mipmap-xxxhdpi-v26/app_adaptive_bg_round.webp'),
    (Join-Path $adaptiveResources 'mipmap-xxxhdpi-v26/topicsgram_tabs_round.png'),
    '-compose', 'Over', '-composite',
    (Join-Path $adaptiveResources 'mipmap-xxxhdpi-v26/app_adaptive_fg_round.webp'),
    '-compose', 'Over', '-composite', '-strip', $preview)
Write-Output "Launcher assets regenerated. Preview: $preview"
