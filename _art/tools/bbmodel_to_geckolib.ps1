# Converts a Blockbench GeckoLib project (.bbmodel) into the three files a GeckoLib mob needs:
#   assets/<ns>/geo/<name>.geo.json, assets/<ns>/animations/<name>.animation.json, textures/entity/<name>.png
#
# The .bbmodel is read only; the texture is copied byte for byte out of the project so hand painted
# pixels are never touched. Blockbench itself does not need to be running.
#
# Usage:  powershell -File bbmodel_to_geckolib.ps1 [-Source <bbmodel>] [-AssetRoot <assets/<ns>>]

param(
    [string]$Source = 'd:/浏览器/Mymods/禁忌嵌合体/26.3/_art/模型/creeper_phantom.bbmodel',
    [string]$AssetRoot = 'd:/浏览器/Mymods/禁忌嵌合体/26.3/common/src/main/resources/assets/forbidden_chimera',
    [string]$ModelName = 'creeper_phantom',
    [string]$Identifier = 'geometry.creeper_phantom',
    [string]$TextureFile = 'creeper_phantom.png',
    # Shifts every bone pivot and cube origin along Y, in model pixels. Used to line the model up with
    # the entity hitbox: this geometry sits 4.75 px (0.297 blocks) too high because its vertical centre
    # (0.547) is above the centre of the 0.9 x 0.5 hitbox (0.25), so the export uses -4.75.
    [double]$ModelYShiftPx = 0.0
)

$ErrorActionPreference = 'Stop'
$inv = [System.Globalization.CultureInfo]::InvariantCulture
$project = Get-Content $Source -Raw -Encoding UTF8 | ConvertFrom-Json

$textureWidth = if ($project.resolution.width) { $project.resolution.width } else { 128 }
$textureHeight = if ($project.resolution.height) { $project.resolution.height } else { 128 }

$groups = @{}
foreach ($g in $project.groups) { $groups[$g.uuid] = $g }
$elementByUuid = @{}
foreach ($e in $project.elements) { $elementByUuid[$e.uuid] = $e }

# --- geometry ---------------------------------------------------------------

$bones = New-Object System.Collections.ArrayList

function ConvertTo-GeoBone($node, $parentName) {
    $group = $groups[$node.uuid]
    $name = if ($group) { $group.name } else { $null }
    if (-not $name) { return }

    # Parent first: GeckoLib links bones to their parent while reading the list, so a child that
    # appears before its parent would lose the hierarchy.
    $bone = [ordered]@{ name = $name }
    if ($parentName) { $bone['parent'] = $parentName }
    if ($group.origin) {
        $bone['pivot'] = @($group.origin[0], ($group.origin[1] + $ModelYShiftPx), $group.origin[2])
    }
    [void]$bones.Add($bone)

    $cubes = New-Object System.Collections.ArrayList
    foreach ($child in $node.children) {
        if ($child -is [string]) {
            $element = $elementByUuid[$child]
            if ($element -and $element.export -ne $false) { [void]$cubes.Add((ConvertTo-GeoCube $element)) }
        } else {
            ConvertTo-GeoBone $child $name
        }
    }

    if ($cubes.Count -gt 0) { $bone['cubes'] = @($cubes) }
}

function ConvertTo-GeoCube($element) {
    $from = @($element.from)
    $to = @($element.to)
    $cube = [ordered]@{
        origin = @($from[0], ($from[1] + $ModelYShiftPx), $from[2])
        size   = @(($to[0] - $from[0]), ($to[1] - $from[1]), ($to[2] - $from[2]))
    }
    if ($element.rotation -and @($element.rotation | Where-Object { $_ -ne 0 }).Count -gt 0) {
        $cube['rotation'] = @($element.rotation)
        if ($element.origin) { $cube['pivot'] = @($element.origin) }
    }
    if ($element.inflate) { $cube['inflate'] = [double]$element.inflate }
    if ($element.mirror_uv) { $cube['mirror'] = $true }

    # Per-face UV, the Bedrock/GeckoLib spelling: {"north": {"uv": [u, v], "uv_size": [w, h]}}
    $uv = [ordered]@{}
    foreach ($face in @('north', 'east', 'south', 'west', 'up', 'down')) {
        $faceUv = $element.faces.$face
        if (-not $faceUv -or -not $faceUv.uv) { continue }
        $rect = @($faceUv.uv)
        $uv[$face] = [ordered]@{
            uv      = @($rect[0], $rect[1])
            uv_size = @(($rect[2] - $rect[0]), ($rect[3] - $rect[1]))
        }
    }
    if ($uv.Count -gt 0) { $cube['uv'] = $uv }
    return $cube
}

foreach ($root in @($project.outliner)) { ConvertTo-GeoBone $root $null }

$geo = [ordered]@{
    format_version       = '1.12.0'
    'minecraft:geometry' = @(
        [ordered]@{
            description = [ordered]@{
                identifier            = $Identifier
                texture_width         = $textureWidth
                texture_height        = $textureHeight
                visible_bounds_width  = 4
                visible_bounds_height = 4
                visible_bounds_offset = @(0, 1, 0)
            }
            bones       = @($bones)
        }
    )
}

# --- animations -------------------------------------------------------------

$animations = [ordered]@{}
foreach ($animation in $project.animations) {
    $loop = switch ($animation.loop) {
        'once' { $false }
        'hold' { 'hold_on_last_frame' }
        default { $true }
    }

    $animBones = [ordered]@{}
    $length = 0.0
    if ($animation.animation_length) { $length = [double]$animation.animation_length }

    foreach ($property in $animation.animators.PSObject.Properties) {
        $animator = $property.Value
        $boneName = if ($groups[$property.Name]) { $groups[$property.Name].name } else { $animator.name }
        $channels = [ordered]@{}

        foreach ($keyframe in $animator.keyframes) {
            $time = [double]$keyframe.time
            if ($time -gt $length) { $length = $time }
            $point = $keyframe.data_points[0]
            if (-not $point) { continue }
            $values = @([double]$point.x, [double]$point.y, [double]$point.z)
            $channel = $keyframe.channel
            if (-not $channels.Contains($channel)) { $channels[$channel] = [ordered]@{} }
            $key = $time.ToString('0.####', $inv)
            if ($keyframe.interpolation -eq 'catmullrom' -or $keyframe.interpolation -eq 'step') {
                $channels[$channel][$key] = [ordered]@{ post = $values; lerp_mode = $keyframe.interpolation }
            } else {
                $channels[$channel][$key] = $values
            }
        }

        if ($channels.Count -gt 0) { $animBones[$boneName] = $channels }
    }

    $entry = [ordered]@{ loop = $loop; animation_length = [math]::Round($length, 4) }
    $entry['bones'] = $animBones
    $animations[$animation.name] = $entry
}

$animationFile = [ordered]@{ format_version = '1.8.0'; animations = $animations }

# --- write ------------------------------------------------------------------

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
# GeckoLib 5 scans assets/<ns>/geckolib/models and .../geckolib/animations; the cache key is the
# bare file name with the folder and the .geo.json / .animation.json suffix stripped.
$geoPath = Join-Path $AssetRoot "geckolib/models/$ModelName.geo.json"
$animationPath = Join-Path $AssetRoot "geckolib/animations/$ModelName.animation.json"
$texturePath = Join-Path $AssetRoot "textures/entity/$TextureFile"
foreach ($path in @($geoPath, $animationPath, $texturePath)) {
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
}

[System.IO.File]::WriteAllText($geoPath, ($geo | ConvertTo-Json -Depth 20), $utf8NoBom)
[System.IO.File]::WriteAllText($animationPath, ($animationFile | ConvertTo-Json -Depth 20), $utf8NoBom)

$source = $project.textures[0].source
$base64 = $source.Substring($source.IndexOf(',') + 1)
[System.IO.File]::WriteAllBytes($texturePath, [Convert]::FromBase64String($base64))

# --- report -----------------------------------------------------------------

Write-Output ("texture : {0}x{1}" -f $textureWidth, $textureHeight)
Write-Output ("bones   : " + (($bones | ForEach-Object { $_.name }) -join ', '))
foreach ($bone in $bones) {
    $cubeCount = if ($bone.cubes) { $bone.cubes.Count } else { 0 }
    Write-Output ("  {0} | parent={1} | cubes={2}" -f $bone.name, $bone.parent, $cubeCount)
}
foreach ($animation in $animations.GetEnumerator()) {
    Write-Output ("anim    : {0} | loop={1} | length={2} | bones={3}" -f $animation.Key, $animation.Value.loop, $animation.Value.animation_length, $animation.Value.bones.Count)
}
foreach ($path in @($geoPath, $animationPath, $texturePath)) {
    Write-Output ("wrote   : {0} ({1} bytes)" -f $path, (Get-Item $path).Length)
}
