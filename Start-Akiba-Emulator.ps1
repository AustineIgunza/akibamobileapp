$ErrorActionPreference = 'Stop'
$sdkRoot = 'C:\Users\Paul Kiragu\AppData\Local\Android\Sdk'
$env:ANDROID_AVD_HOME = Join-Path $PSScriptRoot '.emulator'
$env:ANDROID_USER_HOME = Join-Path $env:ANDROID_AVD_HOME 'android-home'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:ADB_VENDOR_KEYS = Join-Path $env:ANDROID_USER_HOME 'adbkey'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
& $adb -P 5038 start-server
Start-Process -FilePath (Join-Path $sdkRoot 'emulator\emulator.exe') -ArgumentList @('-avd','Akiba_Phone','-no-snapshot','-gpu','swiftshader','-scale','0.65','-skip-adb-auth') -WindowStyle Normal
& $adb -P 5038 wait-for-device
while ((& $adb -P 5038 shell getprop sys.boot_completed) -notmatch '1') { Start-Sleep -Seconds 3 }
& $adb -P 5038 reverse tcp:4000 tcp:4000
& $adb -P 5038 install -r (Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk')
& $adb -P 5038 shell am start -n com.example.mobilefinanceappapplication32/.MainActivity



