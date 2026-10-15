# 生成 GameTest 用的空（纯空气）结构模板 NBT。
#
# 字节布局按 1.21.1 原生结构文件逐字节比对得到。字段类型必须一致，否则加载时抛
# ReportedNbtException / EOFException。与原生一致的关键点：
#   根 TAG_Compound，根名称为空
#     DataVersion TAG_Int           —— 可省略；本生成器默认省略，与原生文件一致
#     size        TAG_List(TAG_Int) —— 元素类型 0x03 写在长度之前
#     entities    TAG_List(TAG_Compound) 空列表
#     blocks      TAG_List(TAG_Compound) 空列表
#     palette     TAG_List(TAG_Compound) 含一个 minecraft:air
#
# 用法：pwsh -File tools/make-empty-structure.ps1 -OutputPath 目标路径 [-SizeX 5 -SizeY 4 -SizeZ 5] [-IncludeDataVersion]
param(
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [int]$SizeX = 5,
    [int]$SizeY = 4,
    [int]$SizeZ = 5,
    [int]$DataVersion = 0,
    [switch]$IncludeDataVersion
)

$ErrorActionPreference = 'Stop'

# NBT 全部为「大端序」。System.IO.BinaryWriter.Write 是「小端序」，
# 直接使用会写出 04 00 而非 00 04，导致 Minecraft 读取长度错位并抛 EOFException。
# 因此所有长度与整数一律走 BinaryPrimitives 的大端写入口。

function Write-BeU16([System.IO.BinaryWriter]$w, [int]$value) {
    $buf = New-Object byte[] 2
    [System.Buffers.Binary.BinaryPrimitives]::WriteUInt16BigEndian($buf, [uint16]$value)
    $w.Write($buf)
}

function Write-BeI32([System.IO.BinaryWriter]$w, [int]$value) {
    $buf = New-Object byte[] 4
    [System.Buffers.Binary.BinaryPrimitives]::WriteInt32BigEndian($buf, [int]$value)
    $w.Write($buf)
}

function Write-Name([System.IO.BinaryWriter]$w, [string]$name) {
    $nb = [System.Text.Encoding]::UTF8.GetBytes($name)
    Write-BeU16 $w $nb.Length
    $w.Write($nb)
}

function Write-TagString([System.IO.BinaryWriter]$w, [string]$name, [string]$value) {
    $w.Write([byte]0x08); Write-Name $w $name
    $vb = [System.Text.Encoding]::UTF8.GetBytes($value)
    Write-BeU16 $w $vb.Length; $w.Write($vb)
}

function Write-TagInt([System.IO.BinaryWriter]$w, [string]$name, [int]$value) {
    $w.Write([byte]0x03); Write-Name $w $name
    Write-BeI32 $w $value
}

# TAG_List(TAG_Int)，元素类型 0x03 在长度之前
function Write-TagIntList([System.IO.BinaryWriter]$w, [string]$name, [int[]]$values) {
    $w.Write([byte]0x09); Write-Name $w $name
    $w.Write([byte]0x03)
    Write-BeI32 $w $values.Length
    foreach ($v in $values) { Write-BeI32 $w $v }
}

# 空 TAG_List：元素类型 0x00、长度 0。
# 逐字节比对 1.21.1 原生结构确认：entities 后为 00 00 00 00 00（元素类型 0x00 + 长度 0）。
function Write-EmptyCompoundList([System.IO.BinaryWriter]$w, [string]$name) {
    $w.Write([byte]0x09); Write-Name $w $name
    $w.Write([byte]0x00)
    Write-BeI32 $w 0
}

$dir = Split-Path -Parent $OutputPath
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

$ms = New-Object System.IO.MemoryStream
$w = New-Object System.IO.BinaryWriter($ms)

$w.Write([byte]0x0A)     # 根 TAG_Compound
Write-BeU16 $w 0         # 根名称为空

if ($IncludeDataVersion) {
    Write-TagInt $w 'DataVersion' $DataVersion
}
Write-TagIntList    $w 'size' @($SizeX, $SizeY, $SizeZ)
Write-EmptyCompoundList $w 'entities'
Write-EmptyCompoundList $w 'blocks'

# palette: TAG_List(TAG_Compound) 一个 minecraft:air
$w.Write([byte]0x09); Write-Name $w 'palette'
$w.Write([byte]0x0A)
Write-BeI32 $w 1
Write-TagString $w 'Name' 'minecraft:air'
$w.Write([byte]0x00)     # 结束 palette 内 compound

$w.Write([byte]0x00)     # 结束根 compound
$w.Flush()

$bytes = $ms.ToArray()
$w.Dispose(); $ms.Dispose()

$fs = [System.IO.File]::Create($OutputPath)
$gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionLevel]::Optimal)
$gz.Write($bytes, 0, $bytes.Length)
$gz.Dispose(); $fs.Dispose()

Write-Output "已生成: $OutputPath"
Write-Output "  size=${SizeX}x${SizeY}x${SizeZ} DataVersion=$DataVersion 未压缩=$($bytes.Length) 字节 磁盘=$((Get-Item $OutputPath).Length) 字节"
