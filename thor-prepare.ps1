param([string]$CacheDirectory = (Join-Path $PSScriptRoot '.thor-cache'))
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null

function Get-VerifiedDependency([string]$Url, [string]$Destination, [string]$Sha256) {
    if (!(Test-Path -LiteralPath $Destination)) {
        & curl.exe --fail --location --silent --show-error $Url --output $Destination
        if ($LASTEXITCODE -ne 0) { throw "Dependency download failed: $Url" }
    }
    $actual = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash
    if ($actual -ne $Sha256) { throw "Dependency checksum mismatch: $Destination" }
}

$archive = Join-Path $CacheDirectory 'remote-desktop-clients-libs-17.tar.gz'
Get-VerifiedDependency 'https://github.com/iiordanov/remote-desktop-clients/releases/download/dependencies/remote-desktop-clients-libs-17.tar.gz' $archive 'EC916DB7E3D89A112EF4B9167E6A3EBF82D52F918DE2E80DC9A6C8E56E51010E'
$core = 'remoteClientLib/jni/libs/deps/FreeRDP/client/Android/Studio/freeRDPCore'
if (!(Test-Path -LiteralPath "$core/src/main/jniLibs/arm64-v8a/libfreerdp-android.so")) {
    # The archive's Unix compatibility symlink is unnecessary for modern AGP.
    & tar.exe --extract --file $archive --directory $PSScriptRoot --exclude '*/src/main/libs' --exclude '*/build/*'
    if ($LASTEXITCODE -ne 0) { throw 'Native dependency extraction failed.' }
}

# Upstream's v17 binaries work with these later Java-only compatibility patches.
$bookmark = "$core/src/main/java/com/freerdp/freerdpcore/domain/BookmarkBase.java"
if (!([IO.File]::ReadAllText((Join-Path $PSScriptRoot $bookmark)).Contains('setGlyphCache'))) {
    & git apply --ignore-whitespace --directory=remoteClientLib/jni/libs/deps/FreeRDP remoteClientLib/jni/libs/19_freerdp_add_glyph_cache_to_bookmark.patch
    if ($LASTEXITCODE -ne 0) { throw 'Could not apply upstream glyph-cache bookmark patch.' }
}
$wrapper = "$core/src/main/java/com/freerdp/freerdpcore/services/LibFreeRDP.java"
if (!([IO.File]::ReadAllText((Join-Path $PSScriptRoot $wrapper)).Contains('flags.isGlyphCache()'))) {
    & git apply --ignore-whitespace --directory=remoteClientLib/jni/libs/deps/FreeRDP remoteClientLib/jni/libs/20_freerdp_toggle_glyph_cache_in_libfreerdp.patch
    if ($LASTEXITCODE -ne 0) { throw 'Could not apply upstream glyph-cache argument patch.' }
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot "$core/src/main/AndroidManifest.xml"), '<?xml version="1.0" encoding="utf-8"?><manifest />', [Text.UTF8Encoding]::new($false))

New-Item -ItemType Directory -Force -Path 'common/aars' | Out-Null
Get-VerifiedDependency 'https://repo.maven.apache.org/maven2/net/zetetic/android-database-sqlcipher/4.5.4/android-database-sqlcipher-4.5.4.aar' (Join-Path $PSScriptRoot 'common/aars/android-database-sqlcipher-4.5.4.aar') 'B7E2C945B9A34C366826330C2AF51800EF399C13DBF7EA02229C6AEDB176978B'
Write-Host 'Thor RDP native and SQLCipher dependencies are ready.'
