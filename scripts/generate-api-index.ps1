# Parses docs/bitwig_docs/BitwigAPI25.txt (concatenated annotated Java sources)
# into extension/src/main/resources/bitwig-api-index.json — the docs index the
# bridge's bw_describe tool and deprecation-refusal errors read at runtime.
#
# Usage: pwsh -File scripts/generate-api-index.ps1
# Rerun manually whenever BitwigAPI25.txt changes; commit the regenerated JSON.
#
# Format facts this parser relies on (verified 2026-07-04):
# - files are concatenated, delimited by 'package ...;' lines, with a literal
#   '-e ' junk line between files
# - top-level type: '^public (interface|class|abstract class|enum) Name...'
# - members are indented 3 spaces; javadoc blocks precede them
# - deprecated members have BOTH an '@Deprecated' annotation line and a
#   javadoc '@deprecated Use {@link #x()} instead.' tag
param(
    [string]$ApiDoc = 'docs/bitwig_docs/BitwigAPI25.txt',
    [string]$OutFile = 'extension/src/main/resources/bitwig-api-index.json'
)

$lines = Get-Content -Path $ApiDoc
$types = [ordered]@{}

$currentType = $null
$pendingDoc = @()      # javadoc lines collected for the next declaration
$pendingDeprecated = $false
$inJavadoc = $false
$pendingSignature = ''  # multi-line member declaration accumulator

function Get-Summary([string[]]$docLines) {
    # First sentence of the javadoc body (tags stripped), single line.
    $body = ($docLines | Where-Object { $_ -notmatch '^\s*\*?\s*@' }) -join ' '
    $body = $body -replace '/\*\*', '' -replace '\*/', '' -replace '^\s*\*\s?', '' -replace '\s+\*\s?', ' '
    $body = ($body -replace '\{@link\s+([^}]+)\}', '$1') -replace '\s{2,}', ' '
    $body = $body.Trim()
    if ($body -match '^(.*?[.!?])(\s|$)') { return $Matches[1].Trim() }
    return $body
}

function Get-Tag([string[]]$docLines, [string]$tag) {
    # Same comment-decoration stripping as Get-Summary (join with a single
    # space so consecutive lines' leading '/**'/'*'/'*/' collapse away).
    # A tag's value runs until the next '@tagName' boundary (whitespace
    # immediately followed by '@word') or end of string — NOT until the next
    # bare '@' character, since '{@link ...}' (the standard deprecation-note
    # idiom, e.g. '@deprecated Use {@link #x()} instead.') contains an inline
    # '@' that is not itself a new tag.
    $joined = ($docLines -join ' ')
    $joined = $joined -replace '/\*\*', '' -replace '\*/', '' -replace '^\s*\*\s?', '' -replace '\s+\*\s?', ' '
    if ($joined -match "@$tag\s+(.*?)(?=\s@\w+|$)") {
        return (($Matches[1] -replace '\{@link\s+([^}]+)\}', '$1') -replace '\s{2,}', ' ').Trim()
    }
    return $null
}

foreach ($raw in $lines) {
    $line = $raw.TrimEnd()
    if ($line -eq '-e' -or $line -eq '') { continue }
    if ($line -match '^package ') { $currentType = $null; $pendingDoc = @(); $pendingDeprecated = $false; $pendingSignature = ''; continue }
    if ($line -match '^import ') { continue }

    if ($line -match '^\s*/\*\*') { $inJavadoc = $true; $pendingDoc = @($line); if ($line -match '\*/') { $inJavadoc = $false }; continue }
    if ($inJavadoc) { $pendingDoc += $line; if ($line -match '\*/') { $inJavadoc = $false }; continue }

    # Top-level type declaration
    if ($line -match '^public (?<abstract>abstract )?(?<kind>interface|class|enum) (?<name>\w+)') {
        $kind = $Matches['kind']
        $name = $Matches['name']
        # Strip the '<...>' type-parameter section (if any) immediately after
        # the type name before matching 'extends'. For a generic declaration
        # like 'interface Bank<ItemType extends ObjectProxy> extends
        # ObjectProxy, Scrollable' the naive first-'extends' match lands
        # INSIDE the type-parameter bound instead of the real supertype
        # list, producing garbage entries and dropping the real parents.
        # Declarations are single-line in this file, so a nesting-aware
        # strip of the first balanced '<...>' group (bounds can themselves
        # be generic, e.g. '<T extends Bank<X>>') is sufficient — and safe
        # even when the first '<' belongs to a supertype's type argument
        # instead (e.g. 'TrackBank extends ChannelBank<Track>'), since only
        # a type's OWN parameter list can contain a nested 'extends' bound.
        $declLine = $line
        $typeParamStart = $declLine.IndexOf('<')
        if ($typeParamStart -ge 0) {
            $depth = 0
            $end = -1
            for ($i = $typeParamStart; $i -lt $declLine.Length; $i++) {
                if ($declLine[$i] -eq '<') { $depth++ }
                elseif ($declLine[$i] -eq '>') { $depth--; if ($depth -eq 0) { $end = $i; break } }
            }
            if ($end -ge 0) {
                $declLine = $declLine.Substring(0, $typeParamStart) + $declLine.Substring($end + 1)
            }
        }
        $ext = @()
        if ($declLine -match 'extends ([^{]+)') {
            $ext = ($Matches[1] -split ',') | ForEach-Object { ($_ -replace '<.*', '').Trim() } | Where-Object { $_ }
        }
        $typeDeprecated = $pendingDeprecated -or ($null -ne (Get-Tag $pendingDoc 'deprecated'))
        $currentType = [ordered]@{
            kind = $kind; extends = @($ext); doc = (Get-Summary $pendingDoc)
            deprecated = $typeDeprecated; methods = [System.Collections.ArrayList]@()
        }
        $types[$name] = $currentType
        $pendingDoc = @(); $pendingDeprecated = $false; $pendingSignature = ''
        continue
    }

    if ($null -eq $currentType) { continue }

    # Member annotations (3-space indent)
    if ($line -match '^\s{3}@Deprecated\b') { $pendingDeprecated = $true; continue }
    if ($line -match '^\s{3}@\w+') { continue }   # @OscMethod, @OscNode, @Override, ...

    # Member declaration (3-space indent), may span lines until ; or {
    if ($pendingSignature -ne '' -or $line -match '^\s{3}\S') {
        $trimmedLine = $line.Trim()
        if ($pendingSignature -eq '' -and $trimmedLine -match '^\}\s*(.*)$') {
            # A lone '}' starting a fresh accumulation is a stray body-closing
            # brace left over from the PREVIOUS member (a constructor's or
            # concrete method's '   }'), not the start of a new declaration —
            # left unhandled it gets glued onto the next signature (e.g.
            # Extension.getHost recorded as "} public HostType getHost()").
            # Drop it; resume from anything else that shared the line.
            $trimmedLine = $Matches[1]
            if ($trimmedLine -eq '') { continue }
        }
        $pendingSignature = ($pendingSignature + ' ' + $trimmedLine).Trim()
        if ($pendingSignature -notmatch '[;{]') { continue }   # declaration continues
        $decl = ($pendingSignature -split '[;{]')[0].Trim()
        $pendingSignature = ''
        # Method: contains '(' and a name before it; skip constants/fields and enum constants
        if ($decl -match '^(?<pre>.*?)\b(?<mname>\w+)\s*\((?<params>.*)\)\s*$' -and $decl -notmatch '=') {
            # Capture named groups into locals immediately: the '$sinceText -match'
            # below (evaluated in this same scope, not inside a function) would
            # otherwise clobber the automatic $Matches variable before it's read.
            $pre = $Matches['pre'].Trim()
            $mname = $Matches['mname']
            if ($pre -eq '' -or $mname -eq $name) { $pendingDoc = @(); $pendingDeprecated = $false; continue }  # enum ctor / constructor
            $replacement = Get-Tag $pendingDoc 'deprecated'
            $sinceText = Get-Tag $pendingDoc 'since'
            $since = $null
            if ($sinceText -match '(\d+)') { $since = [int]$Matches[1] }
            $isDeprecated = $pendingDeprecated -or ($null -ne $replacement)
            [void]$currentType['methods'].Add([ordered]@{
                name = $mname
                signature = ($decl -replace '\s+', ' ')
                doc = (Get-Summary $pendingDoc)
                since = $since
                deprecated = $isDeprecated
                replacement = $replacement
            })
        }
        $pendingDoc = @(); $pendingDeprecated = $false
        continue
    }
}

# ---- resolve deprecated-replacement chains within a type ----
# A '@deprecated ... Use {@link #x()} instead.' note sometimes points at a
# method that is ITSELF deprecated (documented separately), e.g.
# TrackBank.getTrack -> getChannel(int) -> getItemAt(int). Chain through
# same-type bare '#method(...)' references (a qualified 'Type#method' link
# is never chained, since '#' there is preceded by a word character) so the
# replacement hint always surfaces the ultimate non-deprecated method where
# one is reachable, instead of pointing callers at another deprecated call.
function Resolve-ReplacementChain($typeMethods, [string]$text, [int]$depth) {
    if ($depth -ge 6 -or [string]::IsNullOrEmpty($text)) { return $text }
    if ($text -notmatch '(?<!\w)#(\w+)\(') { return $text }
    $targetName = $Matches[1]
    $target = $typeMethods | Where-Object { $_.name -eq $targetName } | Select-Object -First 1
    if ($null -eq $target -or -not $target.deprecated -or [string]::IsNullOrEmpty($target.replacement)) { return $text }
    if ($target.replacement -eq $text) { return $text }   # guard against a trivial self-reference
    $chained = Resolve-ReplacementChain $typeMethods $target.replacement ($depth + 1)
    return "$text (in turn deprecated: $chained)"
}

foreach ($typeName in @($types.Keys)) {
    $type = $types[$typeName]
    foreach ($m in $type.methods) {
        if ($m.deprecated -and $m.replacement) {
            $m['replacement'] = Resolve-ReplacementChain $type.methods $m.replacement 0
        }
    }
}

$result = [ordered]@{ types = $types }
$json = $result | ConvertTo-Json -Depth 8
New-Item -ItemType Directory -Force -Path (Split-Path $OutFile) | Out-Null
Set-Content -Path $OutFile -Value $json -Encoding UTF8

# ---- validation gate ----
$typeCount = $types.Count
$methodCount = ($types.Values | ForEach-Object { $_.methods.Count } | Measure-Object -Sum).Sum
$deprecatedCount = ($types.Values | ForEach-Object { @($_.methods | Where-Object { $_.deprecated }).Count } | Measure-Object -Sum).Sum
Write-Host "types=$typeCount methods=$methodCount deprecated=$deprecatedCount -> $OutFile"

$fail = @()
if ($typeCount -lt 250) { $fail += "expected >=250 types, got $typeCount" }
if (-not $types.Contains('Transport')) { $fail += 'missing type Transport' }
if (-not $types.Contains('SettableRangedValue')) { $fail += 'missing type SettableRangedValue' }
$tb = $types['TrackBank']
$getTrack = $tb.methods | Where-Object { $_.name -eq 'getTrack' } | Select-Object -First 1
if ($null -eq $getTrack -or -not $getTrack.deprecated -or $getTrack.replacement -notmatch 'getItemAt') {
    $fail += 'TrackBank.getTrack should be deprecated with replacement mentioning getItemAt'
}
$sub = $types['Subscribable'].methods | Where-Object { $_.name -eq 'subscribe' } | Select-Object -First 1
if ($null -eq $sub -or $sub.deprecated) { $fail += 'Subscribable.subscribe should exist and not be deprecated' }
if ($fail.Count -gt 0) { $fail | ForEach-Object { Write-Error $_ }; exit 1 }
Write-Host 'validation OK' -ForegroundColor Green
