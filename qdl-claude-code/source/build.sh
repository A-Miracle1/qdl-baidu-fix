#!/bin/sh
set -e
cd "$(dirname "$0")"
AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf out outdex; mkdir -p out outdex
javac -nowarn -source 8 -target 8 -bootclasspath "$AJ" -d out src/com/dsh/qdl/*.java
java -cp /root/build/r8.jar com.android.tools.r8.D8 --lib "$AJ" --min-api 21 --output outdex $(find out -name '*.class')
aapt package -f -M AndroidManifest.xml -S res -I "$AJ" -A assets -F app.unsigned.apk
(cd outdex && aapt add ../app.unsigned.apk classes.dex)
zipalign -f 4 app.unsigned.apk app-aligned.apk
apksigner sign --ks qdl.keystore --ks-pass pass:qdlqdl123 --key-pass pass:qdlqdl123 --out app.apk app-aligned.apk
echo "built: $(pwd)/app.apk"
