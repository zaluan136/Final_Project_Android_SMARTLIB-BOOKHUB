#!/usr/bin/env python3
"""Run the pinned wrapper with the cloud SDK, JDK and inherited proxy trust."""
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlparse

root = Path("/workspace/toolchains")
environment = os.environ.copy()
environment.update(JAVA_HOME=str(root / "jdk-25.0.5.0.3"), ANDROID_HOME=str(root / "android-sdk"), ANDROID_USER_HOME=str(root / "android-user"), GRADLE_USER_HOME=str(root / "gradle-user"))
options = ["-Duser.home=" + str(root / "java-user"), "-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"]
proxy = urlparse(environment.get("HTTPS_PROXY", environment.get("https_proxy", "")))
if proxy.hostname:
    for protocol in ["http", "https"]:
        options += [f"-D{protocol}.proxyHost={proxy.hostname}", f"-D{protocol}.proxyPort={proxy.port or 80}"]
environment["JAVA_TOOL_OPTIONS"] = " ".join(options)
result = subprocess.run(["bash", "gradlew", "--no-daemon", "--max-workers=2"] + sys.argv[1:], cwd=Path(__file__).resolve().parents[1], env=environment)
sys.exit(result.returncode)
