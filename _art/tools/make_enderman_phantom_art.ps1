# 末影人幻翼（enderman_phantom）美术资产生成脚本 —— 确定性工序，可重跑。
#
# 依据（强制规范 §9.1.1：部位判定必须来自数据，禁止目测）：
#   解析 creeper_phantom.geo.json 后，模型由三部分组成：
#     · 幻翼部分：root / left_wing* / right_wing* / tail*（UV 96,12 与 64,64 —— 保持不动）
#     · 苦力怕部分：head（8x8x8，面 UV (8,8)/(0,8)/(24,8)/(16,8)/(8,0)/(16,0) = 原版头布局）
#                   与 body（box UV (0,64) 的 6x5x16 + 5x3.5x2）
#   本次把「苦力怕部分」换成末影人：头沿用原版头 UV 布局（末影人头同布局，只换像素），
#   躯干改为末影人比例（8 宽）并迁到 v<64 半区的空闲位置（box UV 48,0 / 48,16）。
#
# 位置铁律（规范 §3.2）：躯干改动后模型在 Y 轴的区间必须与原来完全一致
#   原来 body: y = 1.25 .. 6.25（模型单位）→ 新 body 同为 1.25 .. 6.25，判定箱对齐不受影响。
#
# 用法：powershell -File make_enderman_phantom_art.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = "d:\浏览器\Mymods\禁忌嵌合体\26.3"
$assets = "$root\common\src\main\resources\assets\forbidden_chimera"
$geoDir = "$assets\geckolib\models"
$texDir = "$assets\textures\entity"
$itemTexDir = "$assets\textures\item"
$vanillaDir = "$root\_art\vanilla"
$clientJar = "D:\浏览器\资源包\.minecraft\versions\模组测试26.3Fabric\模组测试26.3Fabric.jar"

# ---------------------------------------------------------------- 1. 取原版末影人贴图
if (-not (Test-Path "$vanillaDir\enderman.png")) {
    $tmp = "$env:TEMP\fc_vanilla_assets"
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $tmp | Out-Null
    tar -xf $clientJar -C $tmp "assets/minecraft/textures/entity/enderman/enderman.png" 2>$null
    Copy-Item "$tmp\assets\minecraft\textures\entity\enderman\enderman.png" "$vanillaDir\enderman.png" -Force
    Write-Host "[1] 已从原版客户端 jar 取出 enderman.png"
} else {
    Write-Host "[1] 已存在 $vanillaDir\enderman.png，跳过解包"
}

# ---------------------------------------------------------------- 2. 生成 enderman_phantom.geo.json
$srcGeo = Get-Content "$geoDir\creeper_phantom.geo.json" -Raw | ConvertFrom-Json
$geo = $srcGeo.'minecraft:geometry'[0]
$geo.description.identifier = "geometry.enderman_phantom"

# body 的两颗 cube 换成末影人躯干（8 宽、Y 区间不变、后端仍接到尾巴根部 z=10）
$bodyBone = $geo.bones | Where-Object { $_.name -eq 'body' }
$torso = $bodyBone.cubes[0]
$torso.origin = @(-4, 1.25, -6)
$torso.size = @(8, 5, 8)
foreach ($f in $torso.uv.PSObject.Properties) { $f.Value.uv = @(48, 0) }
# 逐面按新尺寸重算 uv_size（box UV 保持起点 48,0，尺寸随面类型变化）
$halfD = 8.0; $w = 8.0; $h = 5.0
$torso.uv.north.uv_size = @($w, $h)
$torso.uv.south.uv_size = @($w, $h)
$torso.uv.east.uv_size  = @($halfD, $h)
$torso.uv.west.uv_size  = @($halfD, $h)
$torso.uv.up.uv_size    = @($w, $halfD)
$torso.uv.down.uv_size  = @($w, $halfD)

$lower = $bodyBone.cubes[1]
$lower.origin = @(-2.5, 2.25, 2)
$lower.size = @(5, 3.5, 8)
$lW = 5.0; $lH = 3.5; $lD = 8.0
foreach ($f in $lower.uv.PSObject.Properties) { $f.Value.uv = @(48, 16) }
$lower.uv.north.uv_size = @($lW, $lH)
$lower.uv.south.uv_size = @($lW, $lH)
$lower.uv.east.uv_size  = @($lD, $lH)
$lower.uv.west.uv_size  = @($lD, $lH)
$lower.uv.up.uv_size    = @($lW, $lD)
$lower.uv.down.uv_size  = @($lW, $lD)

$json = $srcGeo | ConvertTo-Json -Depth 100 -Compress
$json = $json -replace '"identifier":"geometry\.creeper_phantom"', '"identifier":"geometry.enderman_phantom"'
[IO.File]::WriteAllText("$geoDir\enderman_phantom.geo.json", $json, (New-Object Text.UTF8Encoding($false)))
Write-Host "[2] 已写出 enderman_phantom.geo.json（body 改为末影人躯干，Y 区间保持 1.25~6.25）"

# 自检：确认翅膀/尾巴骨骼与 cube 完全未改动
$newGeo = Get-Content "$geoDir\enderman_phantom.geo.json" -Raw | ConvertFrom-Json
$newBones = $newGeo.'minecraft:geometry'[0].bones
$kept = @('root', 'left_wing_base', 'left_wing_tip', 'right_wing_base', 'right_wing_tip', 'tail_base', 'tail_tip')
foreach ($n in $kept) {
    $a = ($geo.bones | Where-Object { $_.name -eq $n }) | ConvertTo-Json -Depth 100 -Compress
    $b = ($newBones | Where-Object { $_.name -eq $n }) | ConvertTo-Json -Depth 100 -Compress
    if ($a -ne $b) { throw "自检失败：骨骼 $n 被改动了" }
}
Write-Host "[2b] 自检通过：7 根幻翼骨骼（root/四翼/两尾）逐字节未变"

# ---------------------------------------------------------------- 3. 生成 enderman_phantom.png
$src = [System.Drawing.Bitmap]::FromFile("$texDir\creeper_phantom.png")
$enderman = [System.Drawing.Bitmap]::FromFile("$vanillaDir\enderman.png")
$out = New-Object System.Drawing.Bitmap($src.Width, $src.Height)
$g = [System.Drawing.Graphics]::FromImage($out)
$g.DrawImage($src, 0, 0)
$g.Dispose()

$changed = 0
$outOfRegion = 0
# 3a. 头：原版头布局 region (0,0)-(31,15) 换成末影人头像素
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        $p = $enderman.GetPixel($x, $y)
        if ($p.A -gt 0) { $out.SetPixel($x, $y, $p); $changed++ }
    }
}
# 3b. 躯干：把两块 box UV 区域用末影人皮肤平铺（源：原版末影人身体区 16,20 起 24x12）
function Fill-FromSkin($x0, $y0, $w, $h) {
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $sx = 16 + ($x % 24)
            $sy = 20 + ($y % 12)
            $p = $enderman.GetPixel($sx, $sy)
            $out.SetPixel($x0 + $x, $y0 + $y, $p)
        }
    }
}
Fill-FromSkin 48 0 32 13
Fill-FromSkin 48 16 26 12

# 3c. 自检：改动像素必须全部落在 头区(v<16,u<32) / 躯干区(u>=48 且 v<=28) 内
for ($y = 0; $y -lt $out.Height; $y++) {
    for ($x = 0; $x -lt $out.Width; $x++) {
        $a = $src.GetPixel($x, $y); $b = $out.GetPixel($x, $y)
        if ($a.ToArgb() -ne $b.ToArgb()) {
            $inHead = ($x -le 31 -and $y -le 15)
            $inTorso = ($x -ge 48 -and $x -le 80 -and (($y -le 13) -or ($y -ge 16 -and $y -le 28)))
            if (-not ($inHead -or $inTorso)) { $outOfRegion++ }
        }
    }
}
$out.Save("$texDir\enderman_phantom.png", [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "[3] 已写出 enderman_phantom.png；头像素复制 $changed px，越界改动 = $outOfRegion px"
if ($outOfRegion -gt 0) { throw "自检失败：有 $outOfRegion 个改动像素落在允许区域之外" }

$src.Dispose(); $enderman.Dispose(); $out.Dispose()

# ---------------------------------------------------------------- 4. 刷怪蛋图标（紫黑色调）
$eggSrc = "$itemTexDir\nuclear_creeper_phantom_spawn_egg.png"
if (Test-Path $eggSrc) {
    $egg = [System.Drawing.Bitmap]::FromFile($eggSrc)
    $eggOut = New-Object System.Drawing.Bitmap($egg.Width, $egg.Height)
    for ($y = 0; $y -lt $egg.Height; $y++) {
        for ($x = 0; $x -lt $egg.Width; $x++) {
            $p = $egg.GetPixel($x, $y)
            # 往紫黑偏：压绿、提蓝
            $r = [Math]::Min(255, [int]($p.R * 0.72))
            $gr = [Math]::Max(0, [int]($p.G * 0.55))
            $b = [Math]::Min(255, [int]($p.B * 1.15) + 24)
            $eggOut.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, $r, $gr, $b))
        }
    }
    $eggOut.Save("$itemTexDir\enderman_phantom_spawn_egg.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $egg.Dispose(); $eggOut.Dispose()
    Write-Host "[4] 已写出 enderman_phantom_spawn_egg.png"
} else {
    Write-Host "[4] 警告：找不到参考蛋贴图，跳过蛋图标"
}

Write-Host "完成。"
