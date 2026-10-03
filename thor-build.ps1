param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$AndroidNdk = $env:ANDROID_NDK_HOME,
    [ValidateSet('Debug','Release')][string]$Configuration = 'Debug',
    [string]$GradleCache = (Join-Path $PSScriptRoot '.thor-cache/gradle')
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Supply a JDK 17 installation.' }
if (!(Test-Path -LiteralPath (Join-Path $AndroidSdk 'platforms/android-36/android.jar'))) { throw 'Install Android SDK Platform 36 in the supplied SDK.' }
if (!(Test-Path -LiteralPath (Join-Path $AndroidSdk 'build-tools/35.0.0/aapt2.exe'))) { throw 'Install Android SDK Build-Tools 35.0.0 in the supplied SDK.' }
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
$env:ANDROID_HOME = (Resolve-Path -LiteralPath $AndroidSdk).Path
$env:GRADLE_USER_HOME = [IO.Path]::GetFullPath($GradleCache)
if ($AndroidNdk) {
    if (!(Test-Path -LiteralPath (Join-Path $AndroidNdk 'source.properties'))) { throw 'The optional Android NDK path is invalid.' }
    $env:THOR_ANDROID_NDK = (Resolve-Path -LiteralPath $AndroidNdk).Path
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'), "sdk.dir=$($env:ANDROID_HOME.Replace('\','/'))`n", [Text.UTF8Encoding]::new($false))
& (Join-Path $PSScriptRoot 'gradlew.bat') ":freeaRDP-app:assemble$Configuration" --no-daemon --console=plain --max-workers=4
if ($LASTEXITCODE -ne 0) { throw "Thor RDP $Configuration build failed." }
Write-Host "APK output: $PSScriptRoot/freeaRDP-app/build/outputs/apk/$($Configuration.ToLowerInvariant())"
