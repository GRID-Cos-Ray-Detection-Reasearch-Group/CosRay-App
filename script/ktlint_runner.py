#!/usr/bin/env python3
"""Invoke the shipped ktlint binary (or batch) so pre-commit can run it cross-platform."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent

# git lfs pull
subprocess.run(["git", "lfs", "install"], check=True)
subprocess.run(["git", "lfs", "pull"], check=True)

if sys.platform.startswith("win"):
    executable = SCRIPT_DIR / "ktlint.bat"
else:
    executable = SCRIPT_DIR / "ktlint"

if not executable.exists():
    raise SystemExit(f"ktlint runner needs {executable.name} in {SCRIPT_DIR}")

if sys.platform.startswith("win"):
    command = [str(executable)]
else:
    command = ["bash", str(executable)]

return_code = subprocess.run(command + sys.argv[1:]).returncode
sys.exit(return_code)
