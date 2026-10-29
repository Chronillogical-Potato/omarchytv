"""Check both widget build entry points without signing or contacting a TV."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parent.parent
out = root / "tizen/out"
out.mkdir(exist_ok=True)
work = Path(tempfile.mkdtemp(prefix="verify-build-", dir=out))
env = dict(os.environ, TIZEN_PROFILE="")
version = ET.parse(root / "tizen/config.xml").getroot().get("version")
filename = "OmarchyTV-tizen-" + version + "-unsigned.wgt"


def run(argv, expected=0, extra_env=None):
    result = subprocess.run(argv, cwd=root, env=extra_env or env, capture_output=True, text=True)
    if result.returncode != expected:
        raise RuntimeError(result.stdout + result.stderr)
    return result


run([sys.executable, str(root / "tizen/build-wgt.py"), str(work / "python")])
run(["bash", str(root / "tizen/build-wgt.sh"), str(work / "shell")])
first = work / "python" / filename
second = work / "shell" / filename
assert first.read_bytes() == second.read_bytes(), "Build entry points must produce the same archive"
with zipfile.ZipFile(first) as archive:
    names = set(archive.namelist())
    assert archive.testzip() is None
    required = {"config.xml", "index.html", "app.js", "data.js", "compat.js", "tizen-shim.js",
                "icon.png", "fonts/caskaydia_mono_regular.ttf", "saver/tte-bundle.js"}
    assert required <= names, "Missing assets: " + str(sorted(required - names))
    assert "appinfo.json" not in names and not any(n.startswith("rooted/") for n in names)
    assert not any(n.lower().endswith((".p12", ".pfx", ".pem", ".key", ".tpk", ".dll")) for n in names)
    html = archive.read("index.html").decode()
    assert html.index('src="tizen-shim.js"') < html.index('src="compat.js"')
    for ref in re.findall(r'(?:src|href)="([^"#]+)"', html):
        if not re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", ref):
            path = ref.split("?", 1)[0].split("#", 1)[0]
            assert path in names, "Missing HTML asset: " + path
    assert archive.read("icon.png").startswith(b"\x89PNG")
    wallpapers = [n for n in names if n.startswith("wallpapers/") and n.endswith(".webp")]
    assert wallpapers and all(archive.read(n).startswith(b"RIFF") for n in wallpapers)

before = hashlib.sha256(first.read_bytes()).hexdigest()
repeat = run([sys.executable, str(root / "tizen/build-wgt.py"), str(work / "python")], expected=2)
assert "already exists" in repeat.stderr
assert hashlib.sha256(first.read_bytes()).hexdigest() == before

stub_dir = work / "bin"
stub_dir.mkdir()
record = work / "signing-request.json"
stub = stub_dir / "tizen"
stub.write_text("#!" + sys.executable + "\nimport json, os, pathlib, sys\n"
                "pathlib.Path(os.environ['BUILD_TEST_RECORD']).write_text(json.dumps(sys.argv[1:]))\n")
stub.chmod(0o755)
signing_env = dict(env, PATH=str(stub_dir) + os.pathsep + env.get("PATH", ""),
                   TIZEN_PROFILE="test profile", BUILD_TEST_RECORD=str(record))
run(["bash", str(root / "tizen/build-wgt.sh"), str(work / "profile")], extra_env=signing_env)
args = json.loads(record.read_text())
assert args == ["package", "-t", "wgt", "-s", "test profile", "-o", str(work / "profile/signed"),
                "--", str(work / "profile/stage")]
assert (work / "profile" / filename).read_bytes() == first.read_bytes()
with zipfile.ZipFile(first) as archive:
    assert all((work / "profile/stage" / name).read_bytes() == archive.read(name) for name in names)

print("PASS: complete identical widgets, HTML assets, no overwrite, and signing CLI contract")
print("Signing used a stub CLI; certificate validation requires the real Samsung tools.")
print("Build evidence retained in " + str(work))
