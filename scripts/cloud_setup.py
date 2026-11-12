#!/usr/bin/env python3
"""Prepare this Linux cloud workspace. Android Studio users use their local SDK/JDK."""
import hashlib
import os
from pathlib import Path
import subprocess
import tarfile
import urllib.parse
import zipfile

ROOT = Path("/workspace/toolchains")
ROOT.mkdir(parents=True, exist_ok=True)

def download(url, filename, expected, algorithm="sha256"):
    target = ROOT / filename
    if not target.exists():
        subprocess.run(["curl", "-fsSL", "--retry", "2", "--max-time", "300", url, "-o", str(target)], check=True)
    actual = hashlib.new(algorithm, target.read_bytes()).hexdigest()
    if actual != expected:
        raise RuntimeError(f"Checksum mismatch for {filename}; do not use this archive")
    return target

jdk = ROOT / "jdk-25.0.5.0.3"
if not jdk.exists():
    archive = download("https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz", "jdk25.tar.gz", "6deafad80454d1f968105b1f582d89b8c2e88a5dfc98116a8226e0ec2fd5df8e")
    with tarfile.open(archive) as stream:
        stream.extractall(ROOT, filter="data")
    if not jdk.exists():
        raise RuntimeError("JDK archive version changed; review the pinned release and checksum")
node = ROOT / "node-v22.23.5-linux-x64"
if not node.exists():
    archive = download("https://nodejs.org/dist/v22.23.5/node-v22.23.5-linux-x64.tar.xz", "node-v22.23.5-linux-x64.tar.xz", "cdeb8670a0c81f51b4dc4ac61919f9b1ad917b304bf8221eb939570663d240f8")
    with tarfile.open(archive) as stream:
        stream.extractall(ROOT, filter="data")
sdk = ROOT / "android-sdk"
cli = sdk / "cmdline-tools/19.0"
if not cli.exists():
    archive = download("https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip", "android-tools-19.zip", "5fdcc763663eefb86a5b8879697aa6088b041e70", "sha1")
    staging = ROOT / "android-cli-staging"
    with zipfile.ZipFile(archive) as stream:
        stream.extractall(staging)
    cli.parent.mkdir(parents=True, exist_ok=True)
    (staging / "cmdline-tools").rename(cli)
for executable in (cli / "bin").iterdir():
    executable.chmod(0o755)
for folder in ["java-user", "android-user/cache", "gradle-user", "npm-cache"]:
    (ROOT / folder).mkdir(parents=True, exist_ok=True)
environment = os.environ.copy()
environment.update(JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk), ANDROID_USER_HOME=str(ROOT / "android-user"))
environment["JAVA_TOOL_OPTIONS"] = "-Duser.home=/workspace/toolchains/java-user -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
args = [str(cli / "bin/sdkmanager"), "--sdk_root=" + str(sdk)]
proxy = urllib.parse.urlparse(environment.get("HTTPS_PROXY", environment.get("https_proxy", "")))
if proxy.hostname:
    args += ["--proxy=http", "--proxy_host=" + proxy.hostname, "--proxy_port=" + str(proxy.port or 80)]
subprocess.run(args + ["--licenses"], input="y\n" * 100, text=True, env=environment, check=True)
subprocess.run(args + ["platform-tools", "build-tools;36.0.0"], input="y\n" * 100, text=True, env=environment, check=True)
# AGP 9.3.3 downloads the SDK 37.0 package using its current repository metadata parser.
print("Cloud toolchain available; now run scripts/cloud_gradle.py to build.")
