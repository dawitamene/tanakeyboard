#!/usr/bin/env python3

import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

COMMIT = "7e3d93af760e27ea6dbc3b7a078d2d9c3335f618"
ARCHIVE_URL = f"https://github.com/hltdi/HornMorpho/raw/{COMMIT}/src/hm/languages/a.tgz"
ARCHIVE_SHA256 = "3274f71da10263acf2bcea54023e4d1088760ea07fedeb7309291d0e2c11e95c"


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def site_packages(python):
    result = subprocess.run(
        [str(python), "-c", "import site; print(site.getsitepackages()[0])"],
        check=True,
        capture_output=True,
        text=True,
    )
    return Path(result.stdout.strip())


def install_language(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, "r:gz") as source:
        members = []
        for member in source.getmembers():
            if member.name == "a" or member.name.startswith("a/"):
                target = (destination / member.name).resolve()
                if os.path.commonpath([destination.resolve(), target]) != str(destination.resolve()):
                    raise RuntimeError(f"unsafe archive member: {member.name}")
                members.append(member)
        source.extractall(destination, members=members)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--venv", type=Path, required=True)
    parser.add_argument("--archive", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    subprocess.run([sys.executable, "-m", "venv", str(args.venv)], check=True)
    python = args.venv / "bin" / "python"
    subprocess.run(
        [str(python), "-m", "pip", "install", "-r", str(root / "requirements.lock")],
        check=True,
    )

    temporary = None
    archive = args.archive
    if archive is None:
        temporary = tempfile.TemporaryDirectory(prefix="hornmorpho-")
        archive = Path(temporary.name) / "a.tgz"
        urllib.request.urlretrieve(ARCHIVE_URL, archive)
    actual = sha256(archive)
    if actual != ARCHIVE_SHA256:
        raise RuntimeError(f"Amharic archive SHA-256 mismatch: expected {ARCHIVE_SHA256}, got {actual}")
    install_language(archive, site_packages(python) / "hm" / "languages")
    subprocess.run(
        [str(python), "-c", "import hm; assert hm.__version__ == '5.3.6'; print(hm.__version__)"],
        check=True,
    )
    if temporary is not None:
        temporary.cleanup()


if __name__ == "__main__":
    main()
