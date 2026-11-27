# Build DSU 缁狅紕鎮婇崳?APK with raw SDK build-tools (no Gradle)
$ErrorActionPreference = "Continue"
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$bt = "$sdk\build-tools\36.0.0"
$androidJar = "$sdk\platforms\android-37.0\android.jar"
$proj = Split-Path -Parent $MyInvocation.MyCommand.Path
$build = "$proj\build"
$outApk = (Join-Path (Split-Path -Parent $proj) "apk") + "\DSUManager-v2.3.apk"

New-Item -ItemType Directory -Force -Path "$build\gen", "$build\classes", "$build\dex" | Out-Null
Remove-Item "$build\classes\*" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "$build\gen\*" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "$build\dex\*" -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "== aapt2 compile =="
& "$bt\aapt2.exe" compile --dir "$proj\res" -o "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "== aapt2 link =="
& "$bt\aapt2.exe" link -o "$build\base.apk" -I "$androidJar" --manifest "$proj\AndroidManifest.xml" -R "$build\res.zip" --java "$build\gen" --min-sdk-version 30 --target-sdk-version 36 --version-code 41 --version-name 2.3 --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "== javac =="
$srcs = Get-ChildItem "$proj\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$gen = Get-ChildItem "$build\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$classes = $srcs + $gen
$libsJars = (@(
  "$proj\libs\api-12.2.0.jar",
  "$proj\libs\aidl-12.2.0.jar",
  "$proj\libs\shared-12.2.0.jar",
  "$proj\libs\provider-12.2.0.jar"
) | ForEach-Object { (Resolve-Path $_).Path }) -join ";"
& javac --release 11 -encoding UTF-8 -nowarn -cp "$androidJar;$libsJars" -d "$build\classes" @classes
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "== d8 =="
$classFiles = Get-ChildItem "$build\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --release --min-api 30 --lib "$androidJar" --output "$build\dex" @classFiles @($libsJars -split ";")
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "== package dex into apk =="
python -c "
import zipfile, shutil, os
base = r'$build\base.apk'
dex  = r'$build\dex\classes.dex'
tmp  = r'$build\withdex.apk'
zin = zipfile.ZipFile(base, 'r')
zout = zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED)
for item in zin.infolist():
    zout.writestr(item, zin.read(item.filename))
zout.write(dex, 'classes.dex')
zout.close(); zin.close()
print('packaged', tmp)
"
if ($LASTEXITCODE -ne 0) { throw "package failed" }

Write-Host "== zipalign =="
& "$bt\zipalign.exe" -f -p 4 "$build\withdex.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "== keystore =="
$ks = "$proj\debug.keystore"
if (-not (Test-Path $ks)) {
  & keytool -genkeypair -keystore "$ks" -alias dsu -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=DSU Manager, OU=Dev, O=DSH, C=CN" 2>&1 | Out-Null
}

Write-Host "== sign =="
& "$bt\apksigner.bat" sign --ks "$ks" --ks-pass pass:android --key-pass pass:android --out "$outApk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
& "$bt\apksigner.bat" verify --print-certs "$outApk" | Select-Object -First 4

Write-Host ("APK: " + $outApk + "  size=" + (Get-Item $outApk).Length)

























