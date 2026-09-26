# Generates the three 16x16 spawn egg item textures in vanilla style: one egg shaped mask, one fixed
# blotch pattern, three colour pairs. Deterministic, so re-running produces byte identical files.
#
# Usage: powershell -File make_spawn_egg_textures.ps1

Add-Type -AssemblyName System.Drawing

$outDir = 'd:/浏览器/Mymods/禁忌嵌合体/26.3/common/src/main/resources/assets/forbidden_chimera/textures/item'
New-Item -ItemType Directory -Force $outDir | Out-Null

# --- shared egg silhouette ---------------------------------------------------
$inside = @{}
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $dx = ($x + 0.5 - 7.5) / 5.4
        $dy = ($y + 0.5 - 8.0) / 6.4
        if (($dx * $dx + $dy * $dy) -le 1.0) { $inside["$x,$y"] = $true }
    }
}

$outline = @{}
foreach ($key in $inside.Keys) {
    $parts = $key.Split(',')
    $x = [int]$parts[0]; $y = [int]$parts[1]
    $edge = $false
    foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
        if (-not $inside.ContainsKey("$($x + $d[0]),$($y + $d[1])")) { $edge = $true }
    }
    if ($edge) { $outline[$key] = $true }
}

# --- shared blotch pattern (vanilla eggs have irregular patches, not noise) ---
$blobs = @(
    @{ x = 5.6; y = 6.0; r = 2.3 },
    @{ x = 10.2; y = 8.6; r = 2.1 },
    @{ x = 6.6; y = 11.4; r = 1.7 },
    @{ x = 4.2; y = 9.6; r = 1.5 },
    @{ x = 10.6; y = 5.0; r = 1.3 }
)
$spot = @{}
foreach ($key in $inside.Keys) {
    if ($outline.ContainsKey($key)) { continue }
    $parts = $key.Split(',')
    $x = [double]$parts[0] + 0.5; $y = [double]$parts[1] + 0.5
    foreach ($b in $blobs) {
        $dx = $x - $b.x; $dy = $y - $b.y
        if ((($dx * $dx + $dy * $dy) -le ($b.r * $b.r)) -and ((($x + $y) % 3) -ne 0)) { $spot[$key] = $true; break }
    }
}

function Scale-Colour($r, $g, $b, $factor) {
    $rr = [Math]::Min(255, [Math]::Max(0, [int]($r * $factor)))
    $gg = [Math]::Min(255, [Math]::Max(0, [int]($g * $factor)))
    $bb = [Math]::Min(255, [Math]::Max(0, [int]($b * $factor)))
    return [System.Drawing.Color]::FromArgb(255, $rr, $gg, $bb)
}

$eggs = @(
    @{ name = 'phantom_rider_creeper_spawn_egg'; base = @(78, 154, 59); spot = @(58, 78, 140) },
    @{ name = 'creeper_thrower_phantom_spawn_egg'; base = @(58, 78, 140); spot = @(78, 154, 59) },
    @{ name = 'creeper_phantom_spawn_egg'; base = @(78, 154, 59); spot = @(217, 207, 174) }
)

foreach ($egg in $eggs) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    try {
        $br = $egg.base[0]; $bg = $egg.base[1]; $bb = $egg.base[2]
        foreach ($key in $inside.Keys) {
            $parts = $key.Split(',')
            $x = [int]$parts[0]; $y = [int]$parts[1]

            if ($outline.ContainsKey($key)) {
                $colour = Scale-Colour $br $bg $bb 0.5
            }
            elseif ($spot.ContainsKey($key)) {
                $sr = $egg.spot[0]; $sg = $egg.spot[1]; $sb = $egg.spot[2]
                # Upper-left of every blotch stays a touch brighter, lower-right a touch darker.
                $factor = if (($x + $y) -lt 16) { 1.15 } else { 0.82 }
                $colour = Scale-Colour $sr $sg $sb $factor
            }
            else {
                # Body shading: light from the upper left.
                $factor = if (($x - $y) -le -4) { 1.18 } elseif (($x - $y) -ge 5) { 0.84 } else { 1.0 }
                $colour = Scale-Colour $br $bg $bb $factor
            }
            $bmp.SetPixel($x, $y, $colour)
        }

        $path = Join-Path $outDir ($egg.name + '.png')
        $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output ("wrote {0} ({1} bytes)" -f $path, (Get-Item $path).Length)
    }
    finally {
        $bmp.Dispose()
    }
}
