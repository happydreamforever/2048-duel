#!/usr/bin/env bash
# Helper for driving the app on an emulator/device: tools/emu.sh <cmd> [args]
#   install <apk>            install the APK
#   prefs <ws-url> [name]    preset server URL + player name (debug builds only, uses run-as)
#   launch                   start the activity
#   shot <file.png>          screenshot
#   tap-text "<text>"        tap the UI node whose text matches (uiautomator dump)
#   swipe <UP|DOWN|LEFT|RIGHT> <cx> <cy> [dist]   swipe gesture centred at cx,cy
#   tap <x> <y>
ADB=${ADB:-$HOME/Android/Sdk/platform-tools/adb}
DEV=${DEV:-emulator-5554}
PKG=com.duel2048.app
A="$ADB -s $DEV"
case "$1" in
  install) $A install -r -t "$2" ;;
  prefs)
    cat > /tmp/duel2048_prefs.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="server">$2</string>
    <string name="name">${3:-Tester}</string>
    <string name="theme">neon</string>
    <boolean name="lowfx" value="${4:-false}" />
    <string name="lang">${5:-}</string>
</map>
XML
    $A push /tmp/duel2048_prefs.xml /data/local/tmp/duel2048.xml >/dev/null
    $A shell "run-as $PKG sh -c 'mkdir -p /data/data/$PKG/shared_prefs && cp /data/local/tmp/duel2048.xml /data/data/$PKG/shared_prefs/duel2048.xml'" ;;
  launch) $A shell am start -W -n $PKG/.MainActivity | grep -E "Status|Error" ;;
  stop) $A shell am force-stop $PKG ;;
  shot) $A exec-out screencap -p > "$2"; ls -la "$2" ;;
  tap) $A shell input tap "$2" "$3" ;;
  tap-text)
    $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    $A pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    python3 - "$2" <<'PY'
import re,sys,subprocess,os
text=sys.argv[1]
xml=open('/tmp/ui.xml',encoding='utf-8',errors='ignore').read()
m=None
for node in re.finditer(r'<node [^>]*>', xml):
    n=node.group(0)
    if f'text="{text}"' in n:
        m=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n); break
if not m: print("NOT FOUND:",text); sys.exit(1)
x=(int(m.group(1))+int(m.group(3)))//2; y=(int(m.group(2))+int(m.group(4)))//2
adb=os.environ.get('ADB', os.path.expanduser('~/Android/Sdk/platform-tools/adb')); dev=os.environ.get('DEV','emulator-5554')
subprocess.run([adb,'-s',dev,'shell','input','tap',str(x),str(y)])
print(f"tapped '{text}' at {x},{y}")
PY
    ;;
  swipe)
    d=${5:-350}; cx=$3; cy=$4
    case "$2" in
      UP)    $A shell input swipe $cx $cy $cx $((cy-d)) 80 ;;
      DOWN)  $A shell input swipe $cx $cy $cx $((cy+d)) 80 ;;
      LEFT)  $A shell input swipe $cx $cy $((cx-d)) $cy 80 ;;
      RIGHT) $A shell input swipe $cx $cy $((cx+d)) $cy 80 ;;
    esac ;;
  tap-edit)
    # tap the Nth EditText (1-based) so `input text` goes there
    $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $A pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    python3 - "$2" <<'PY'
import re,sys,subprocess,os
n=int(sys.argv[1]); xml=open('/tmp/ui.xml',encoding='utf-8',errors='ignore').read()
edits=[m for m in re.finditer(r'<node [^>]*class="android.widget.EditText"[^>]*>', xml)]
if len(edits)<n: print("NOT ENOUGH EDIT FIELDS:", len(edits)); sys.exit(1)
b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', edits[n-1].group(0))
x=(int(b.group(1))+int(b.group(3)))//2; y=(int(b.group(2))+int(b.group(4)))//2
adb=os.environ.get('ADB', os.path.expanduser('~/Android/Sdk/platform-tools/adb')); dev=os.environ.get('DEV','emulator-5554')
subprocess.run([adb,'-s',dev,'shell','input','tap',str(x),str(y)]); print(f"tapped edit field {n} at {x},{y}")
PY
    ;;
  texts)
    $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $A pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    grep -o 'text="[^"]*" [^>]*bounds="[^"]*"' /tmp/ui.xml | sed -E 's/ resource-id.*bounds=/ bounds=/' | grep -v 'text=""' | head -40 ;;
  *) echo "unknown command"; exit 1 ;;
esac
