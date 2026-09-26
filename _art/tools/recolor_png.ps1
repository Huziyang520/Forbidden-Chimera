# 按颜色表替换一张 PNG 的像素颜色（保留 alpha），用来给刷怪蛋之类的小图标换色。
#
# 用法:
#   powershell -ExecutionPolicy Bypass -File recolor_png.ps1 -In <源图> -Out <输出png> `
#       -ColorMap "16,36,58=20,12,28;24,64,110=40,22,56;46,111,184=78,38,104"
#
# 说明: 只替换表里列出的颜色，其余像素原样保留，所以不会误伤描边或透明区。
#       源色与目标色之间用 "=" 分隔（不要用 ">"：命令行会把尖括号当重定向吃掉）。
param(
    [Parameter(Mandatory = $true)][string]$In,
    [Parameter(Mandatory = $true)][string]$Out,
    # 注意: 参数名不能叫 $Map，PowerShell 变量名不区分大小写，会和下面的 $map 撞车。
    [Parameter(Mandatory = $true)][string]$ColorMap
)

Add-Type -AssemblyName System.Drawing

$map = @{}
foreach ($entry in $ColorMap.Split(';')) {
    if ([string]::IsNullOrWhiteSpace($entry)) { continue }
    $parts = $entry.Split('=')
    if ($parts.Count -ne 2) { throw "颜色表项格式不对: $entry" }
    $src = $parts[0].Trim()
    $dst = $parts[1].Trim().Split(',')
    $map[$src] = [System.Drawing.Color]::FromArgb(255, [int]$dst[0], [int]$dst[1], [int]$dst[2])
}

$srcBitmap = [System.Drawing.Bitmap]::FromFile($In)
try {
    $w = $srcBitmap.Width
    $h = $srcBitmap.Height
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $data = $srcBitmap.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $stride = $data.Stride
    $bytes = New-Object byte[] ($stride * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    $srcBitmap.UnlockBits($data)
}
finally {
    $srcBitmap.Dispose()
}

$replaced = 0
for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $i = $y * $stride + $x * 4
        if ($bytes[$i + 3] -eq 0) { continue }
        $key = ("{0},{1},{2}" -f $bytes[$i + 2], $bytes[$i + 1], $bytes[$i])
        if (-not $map.ContainsKey($key)) { continue }
        $c = $map[$key]
        $bytes[$i] = $c.B
        $bytes[$i + 1] = $c.G
        $bytes[$i + 2] = $c.R
        $replaced++
    }
}

$dst = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$dstData = $dst.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
[System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $dstData.Scan0, $bytes.Length)
$dst.UnlockBits($dstData)

$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$dst.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$dst.Dispose()

Write-Output ("已写出 {0}，替换 {1} 个像素" -f $Out, $replaced)
