"""Cloud-only SDK bootstrap. Never stores proxy credentials or disables TLS checks."""
import hashlib
import os
from pathlib import Path
import subprocess
import urllib.parse
import xml.etree.ElementTree as ET
import zipfile
import tarfile

sdk = Path(os.environ['ANDROID_HOME'])
tools = Path('/workspace/tooling')
tools.mkdir(exist_ok=True)
jdk = tools / 'jdk21'
if not (jdk / 'bin/javac').exists():
    release = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/'
    filename = 'OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz'
    archive = tools / 'jdk.tar.gz'
    checksum = tools / 'jdk.sha256'
    subprocess.run(['curl', '-fsSL', '--retry', '2', release + filename, '-o', str(archive)], check=True)
    subprocess.run(['curl', '-fsSL', '--retry', '2', release + filename + '.sha256.txt', '-o', str(checksum)], check=True)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != checksum.read_text().split()[0]:
        raise RuntimeError('JDK SHA-256 did not match')
    jdk.mkdir(exist_ok=True)
    with tarfile.open(archive) as t:
        for member in t.getmembers():
            parts = member.name.split('/', 1)
            if len(parts) == 2 and parts[1]:
                member.name = parts[1]
                t.extract(member, jdk, filter='data')

manager = sdk / 'cmdline-tools/latest/bin/sdkmanager'
if not manager.exists():
    archive = tools / 'android-tools.zip'
    manifest = tools / 'android-repository.xml'
    subprocess.run(['curl', '-fsSL', '--retry', '2', 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip', '-o', str(archive)], check=True)
    subprocess.run(['curl', '-fsSL', '--retry', '2', 'https://dl.google.com/android/repository/repository2-1.xml', '-o', str(manifest)], check=True)
    checksum = None
    for node in ET.parse(manifest).iter():
        if node.tag.endswith('archive'):
            url = next((x.text for x in node.iter() if x.tag.endswith('url')), None)
            if url == 'commandlinetools-linux-11076708_latest.zip':
                checksum = next(x.text for x in node.iter() if x.tag.endswith('checksum'))
                break
    if not checksum or hashlib.sha1(archive.read_bytes()).hexdigest() != checksum:
        raise RuntimeError('Android SDK repository checksum did not match')
    staging = tools / 'sdk-extract'
    with zipfile.ZipFile(archive) as z:
        z.extractall(staging)
    (sdk / 'cmdline-tools').mkdir(exist_ok=True)
    (staging / 'cmdline-tools').rename(sdk / 'cmdline-tools/latest')
    for p in (sdk / 'cmdline-tools/latest/bin').iterdir():
        p.chmod(0o755)

env = os.environ.copy()
proxy = urllib.parse.urlsplit(env.get('HTTPS_PROXY', ''))
if proxy.hostname:
    if proxy.username or proxy.password:
        raise RuntimeError('Authenticated proxies need platform Java proxy configuration')
    env['SDKMANAGER_OPTS'] = f'-Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port or 80} -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port or 80}'
    # Machine-local settings; no credentials, and never checked into Git.
    properties = Path(env['GRADLE_USER_HOME']) / 'gradle.properties'
    properties.write_text(f'systemProp.https.proxyHost={proxy.hostname}\nsystemProp.https.proxyPort={proxy.port or 80}\nsystemProp.http.proxyHost={proxy.hostname}\nsystemProp.http.proxyPort={proxy.port or 80}\nsystemProp.http.nonProxyHosts=localhost|127.*\n')
subprocess.run([str(manager), f'--sdk_root={sdk}', '--licenses'], input='y\n' * 100, text=True, env=env, check=True, stdout=subprocess.DEVNULL)
subprocess.run([str(manager), f'--sdk_root={sdk}', 'platforms;android-35', 'build-tools;35.0.0', 'platform-tools'], env=env, check=True)
