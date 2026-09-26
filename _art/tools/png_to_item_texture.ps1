# 把一张带纯色键背景（默认洋红系）的 AI 生成图，抠底并缩成 Minecraft 物品贴图（默认 16x16）。
#
# 用法:
#   powershell -ExecutionPolicy Bypass -File png_to_item_texture.ps1 -In <源图> -Out <输出png> [-Size 16]
#
# 说明:
#   - 键色判定按"色相 + 亮度关系"（R 高、B 中高、G 很低、R>B），比写死 RGB 更耐受 AI 出图的偏差：
#     实测同一张图里背景洋红就是 R179 G42 B142 到 R199 G17 B154 的大范围。
#   - 主体包围盒按"该行/列至少有该方向尺寸 25% 的主体像素"判定，右下角的小水印不会把裁剪框拉歪。
#   - 缩放用区域平均（盒式），只对不透明像素取平均，避免边缘出现黑边。
param(
    [Parameter(Mandatory = $true)][string]$In,
    [Parameter(Mandatory = $true)][string]$Out,
    [int]$Size = 16,
    # Area = 区域平均（更稳，适合摄影/插画）；Nearest = 取格子中心像素（更脆更像素风，适合 AI 像素画）
    [ValidateSet('Area', 'Nearest')][string]$Sampling = 'Area'
)

Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Bitmap]::FromFile($In)
try {
    $w = $src.Width
    $h = $src.Height

    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $data = $src.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $stride = $data.Stride
    $bytes = New-Object byte[] ($stride * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    $src.UnlockBits($data)
}
finally {
    $src.Dispose()
}

# 键色（洋红系背景）判定。数组是 BGRA 顺序。
function Test-Background([int]$pixelIndex) {
    $b = $bytes[$pixelIndex]
    $g = $bytes[$pixelIndex + 1]
    $r = $bytes[$pixelIndex + 2]
    return ($g -lt 90 -and $r -gt 140 -and $b -gt 110 -and $r -gt $b)
}

$rowMin = [int]($w * 0.25)
$colMin = [int]($h * 0.25)

$firstX = -1; $lastX = -1
$firstY = -1; $lastY = -1

for ($y = 0; $y -lt $h; $y++) {
    $count = 0
    $rowBase = $y * $stride
    for ($x = 0; $x -lt $w; $x++) {
        if (-not (Test-Background ($rowBase + $x * 4))) { $count++ }
    }
    if ($count -ge $rowMin) {
        if ($firstY -lt 0) { $firstY = $y }
        $lastY = $y
    }
}

for ($x = 0; $x -lt $w; $x++) {
    $count = 0
    for ($y = 0; $y -lt $h; $y++) {
        if (-not (Test-Background ($y * $stride + $x * 4))) { $count++ }
    }
    if ($count -ge $colMin) {
        if ($firstX -lt 0) { $firstX = $x }
        $lastX = $x
    }
}

if ($firstX -lt 0 -or $firstY -lt 0) {
    throw "没有找到主体像素（整张图都是键色？）"
}

$boxW = $lastX - $firstX + 1
$boxH = $lastY - $firstY + 1
Write-Output ("主体包围盒: x={0} y={1} {2}x{3}" -f $firstX, $firstY, $boxW, $boxH)

$dst = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$dstRect = New-Object System.Drawing.Rectangle 0, 0, $Size, $Size
$dstData = $dst.LockBits($dstRect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$outStride = $dstData.Stride
$outBytes = New-Object byte[] ($outStride * $Size)

for ($oy = 0; $oy -lt $Size; $oy++) {
    $y0 = $firstY + [Math]::Floor($oy * $boxH / $Size)
    $y1 = $firstY + [Math]::Floor(($oy + 1) * $boxH / $Size)
    if ($y1 -le $y0) { $y1 = $y0 + 1 }

    for ($ox = 0; $ox -lt $Size; $ox++) {
        $x0 = $firstX + [Math]::Floor($ox * $boxW / $Size)
        $x1 = $firstX + [Math]::Floor(($ox + 1) * $boxW / $Size)
        if ($x1 -le $x0) { $x1 = $x0 + 1 }

        $sumR = 0.0; $sumG = 0.0; $sumB = 0.0; $n = 0
        if ($Sampling -eq 'Nearest') {
            $sx = [Math]::Min($x1 - 1, [Math]::Floor(($x0 + $x1) / 2))
            $sy = [Math]::Min($y1 - 1, [Math]::Floor(($y0 + $y1) / 2))
            $i = $sy * $stride + $sx * 4
            if (-not (Test-Background $i)) {
                $sumB += $bytes[$i]
                $sumG += $bytes[$i + 1]
                $sumR += $bytes[$i + 2]
                $n = 1
            }
        }
        else {
            for ($y = $y0; $y -lt $y1; $y++) {
                $rowBase = $y * $stride
                for ($x = $x0; $x -lt $x1; $x++) {
                    $i = $rowBase + $x * 4
                    if (Test-Background $i) { continue }
                    $sumB += $bytes[$i]
                    $sumG += $bytes[$i + 1]
                    $sumR += $bytes[$i + 2]
                    $n++
                }
            }
        }

        $o = $oy * $outStride + $ox * 4
        if ($n -eq 0) {
            $outBytes[$o] = 0; $outBytes[$o + 1] = 0; $outBytes[$o + 2] = 0; $outBytes[$o + 3] = 0
        }
        else {
            $outBytes[$o] = [byte][Math]::Round($sumB / $n)
            $outBytes[$o + 1] = [byte][Math]::Round($sumG / $n)
            $outBytes[$o + 2] = [byte][Math]::Round($sumR / $n)
            $outBytes[$o + 3] = 255
        }
    }
}

# 色阶拉伸：AI 出图整体又暗又灰，区域平均后更糊，拉满动态范围 16x16 才读得清。
$minV = 255; $maxV = 0
for ($oy = 0; $oy -lt $Size; $oy++) {
    for ($ox = 0; $ox -lt $Size; $ox++) {
        $o = $oy * $outStride + $ox * 4
        if ($outBytes[$o + 3] -eq 0) { continue }
        for ($k = 0; $k -lt 3; $k++) {
            $v = $outBytes[$o + $k]
            if ($v -lt $minV) { $minV = $v }
            if ($v -gt $maxV) { $maxV = $v }
        }
    }
}
if ($maxV -gt $minV) {
    $scale = 255.0 / ($maxV - $minV)
    for ($oy = 0; $oy -lt $Size; $oy++) {
        for ($ox = 0; $ox -lt $Size; $ox++) {
            $o = $oy * $outStride + $ox * 4
            if ($outBytes[$o + 3] -eq 0) { continue }
            for ($k = 0; $k -lt 3; $k++) {
                $v = [Math]::Round(($outBytes[$o + $k] - $minV) * $scale)
                if ($v -lt 0) { $v = 0 }
                if ($v -gt 255) { $v = 255 }
                $outBytes[$o + $k] = [byte]$v
            }
        }
    }
    Write-Output ("色阶拉伸: {0}..{1}" -f $minV, $maxV)
}

[System.Runtime.InteropServices.Marshal]::Copy($outBytes, 0, $dstData.Scan0, $outBytes.Length)
$dst.UnlockBits($dstData)

$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$dst.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$dst.Dispose()

Write-Output ("已写出 {0} ({1}x{1})" -f $Out, $Size)
