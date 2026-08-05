# Harvests native-device Name / Category / UUID triples from Bitwig .bwpreset files.
# .bwpreset headers are BtWg-format: length-prefixed text fields, scannable as ASCII.
#
# Usage:  pwsh -File scripts/scan-device-uuids.ps1 [-Root <folder>]
# Output: TSV lines "Name<TAB>Category<TAB>uuid", sorted, unique by UUID.
#
# To add a missing device to the catalog:
#   1. In Bitwig, put the device on a track, right-click it > Save Preset.
#   2. Rerun this script.
#   3. Copy the new line into
#      extension/src/main/java/org/wigout/mcp/common/data/DeviceCatalog.java
#
# Caveat: field values are length-prefixed binary; this parser is a heuristic
# (a printable length byte can prefix-corrupt long names). Output is curation
# input reviewed by a human — never shipped raw.
param(
    [string]$Root = (Join-Path $env:OneDrive 'Documents\Bitwig Studio')
)

$sentinel = [string][char]1
$results = @{}

$files = Get-ChildItem -Path $Root -Recurse -File -Filter '*.bwpreset' -ErrorAction SilentlyContinue
foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $take = [Math]::Min($bytes.Length, 4096)
    $text = [System.Text.Encoding]::ASCII.GetString($bytes, 0, $take)
    # Replace non-printable bytes with a sentinel so field values (printable, may contain spaces) can be isolated.
    $clean = [regex]::Replace($text, '[^\x20-\x7E]', $sentinel)

    # (?<!referenced_) skips the referenced_device_ids list; first match is the primary device.
    $uuid = [regex]::Match($clean, '(?<!referenced_)device_id.{1,8}?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})')
    $name = [regex]::Match($clean, "device_name$sentinel+([\x20-\x7E]+?)$sentinel")
    $category = [regex]::Match($clean, "device_category$sentinel+([\x20-\x7E]+?)$sentinel")

    if ($uuid.Success -and $name.Success) {
        $key = $uuid.Groups[1].Value.ToLowerInvariant()
        if (-not $results.ContainsKey($key)) {
            $results[$key] = [pscustomobject]@{
                Name     = $name.Groups[1].Value.Trim()
                Category = if ($category.Success) { $category.Groups[1].Value.Trim() } else { '' }
                Uuid     = $key
            }
        }
    }
}

$results.Values | Sort-Object Name | ForEach-Object { "$($_.Name)`t$($_.Category)`t$($_.Uuid)" }
Write-Host "`n$($results.Count) unique devices found under $Root" -ForegroundColor Green
