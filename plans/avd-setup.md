# AVD setup — TanaLowRam

1 GB RAM emulator for low-end memory verification.

## Prerequisites
- Android SDK at `~/Library/Android/sdk`.
- `cmdline-tools/latest/bin` on PATH. If missing, install via Android Studio SDK Manager, or download:
  ```
  curl -L https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip -o /tmp/cmdline-tools.zip
  unzip -q /tmp/cmdline-tools.zip -d /tmp/ct
  mkdir -p ~/Library/Android/sdk/cmdline-tools/latest
  mv /tmp/ct/cmdline-tools/* ~/Library/Android/sdk/cmdline-tools/latest/
  ```
- PATH:
  ```
  export PATH=~/Library/Android/sdk/cmdline-tools/latest/bin:~/Library/Android/sdk/emulator:~/Library/Android/sdk/platform-tools:$PATH
  ```

## Install system image
```
sdkmanager --install "system-images;android-35;google_apis;x86_64"
sdkmanager --licenses
```

## Create AVD
```
avdmanager create avd -n TanaLowRam -k "system-images;android-35;google_apis;x86_64" -d pixel_3a --force
```

## Patch config.ini
Edit `~/.android/avd/TanaLowRam.avd/config.ini`:
```
hw.ramSize=1024
vm.heapSize=32
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.cpu.ncore=1
disk.dataPartition.size=2048
```

## Boot
```
emulator -avd TanaLowRam -no-window -no-snapshot -no-audio -no-boot-anim &
adb wait-for-device
adb shell while [[ "$(getprop sys.boot_completed)" != "1" ]]; do sleep 1; done
```

## Verify
```
adb shell getprop ro.product.model
adb shell dumpsys meminfo | head -20
```

## Notes
- `isLowRamDevice` may not be set on a phone AVD even with 1024 MB RAM; rely on the `totalMem < 1GB` fallback in code for emulator parity.
- Cold-boot each session for reproducible numbers.
