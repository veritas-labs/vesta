#!/usr/bin/env bash
# Builds Vesta-<version>-x86_64.AppImage from the Compose Desktop app image.
# Needs `appimagetool` on PATH (https://github.com/AppImage/appimagetool/releases) and runs on Linux.
# Output: desktopApp/build/appimage/
set -euo pipefail

cd "$(dirname "$0")/.."
command -v appimagetool >/dev/null || { echo "appimagetool not found on PATH" >&2; exit 1; }

./gradlew :desktopApp:createDistributable

app_dir=desktopApp/build/compose/binaries/main/app/Vesta
out=desktopApp/build/appimage
appdir=$out/Vesta.AppDir
version=$(grep -E '^vestaVersion' gradle.properties | head -1 | cut -d= -f2 | tr -d ' ')

rm -rf "$out" && mkdir -p "$appdir/usr"
cp -r "$app_dir"/. "$appdir/usr/"
cp branding/app-icon.png "$appdir/vesta.png"

cat > "$appdir/AppRun" <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/Vesta" "$@"
RUN
chmod +x "$appdir/AppRun"

# StartupWMClass matches the window class main.kt sets, so the shell shows the icon.
cat > "$appdir/vesta.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Vesta
Exec=Vesta
Icon=vesta
Categories=Utility;
StartupWMClass=vesta-Vesta
DESKTOP

ARCH=x86_64 appimagetool "$appdir" "$out/Vesta-${version:-dev}-x86_64.AppImage"
echo "Wrote $out"
