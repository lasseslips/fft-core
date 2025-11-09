import argparse
import subprocess
import sys
import tempfile
import platform
from pathlib import Path
import os

# run_vivado_script.py
# Run Vivado in batch by calling Xilinx's settings script first so the environment is set.
# Usage: python run_vivado_script.py [--version 2023.1] [--tcl path/to/build.tcl] [--dry-run]
# If --version is not given, the latest version found under typical installation paths is used.
# If --tcl is not given, the script looks for build.tcl in the same directory as this script.
# If build.tcl is not found, the script will exit with an error.
# --dry-run will print the command instead of executing it.
# Works on both Windows and Linux.

def find_vivado_root():
    """Find Vivado installation root directory based on platform."""
    candidates = []
    if "XILINX" in os.environ:
        candidates.append(Path(os.environ["XILINX"]))
    
    if platform.system() == "Windows":
        candidates.extend([
            Path("C:/Xilinx/Vivado"),
            Path("C:/Xilinx")
        ])
        default = Path("C:/Xilinx/Vivado")
    else:  # Linux/Unix
        candidates.extend([
            Path("/tools/Xilinx/Vivado"),
            Path("/opt/Xilinx/Vivado"),
            Path(f"{os.path.expanduser('~')}/Xilinx/Vivado")
        ])
        default = Path("/tools/Xilinx/Vivado")
    
    for c in candidates:
        if c.exists():
            return c
    return default

def choose_version(root: Path, requested: str | None):
    if requested:
        candidate = root / requested
        if candidate.exists():
            return candidate
        raise FileNotFoundError(f"Requested Vivado version not found: {candidate}")
    if not root.exists():
        raise FileNotFoundError(f"Vivado root not found: {root}")
    versions = [d for d in root.iterdir() if d.is_dir()]
    if not versions:
        raise FileNotFoundError(f"No Vivado versions found under {root}")
    # sort by name (works if versions are like 2023.1, 2022.2, ...)
    versions.sort(key=lambda p: p.name, reverse=True)
    return versions[0]

def build_command(settings_script: Path, tcl_path: Path):
    """Return the components needed for running Vivado with proper environment setup."""
    return [str(settings_script), str(tcl_path)]

def get_settings_script(vivado_dir: Path):
    """Get the appropriate settings script based on platform."""
    if platform.system() == "Windows":
        settings = vivado_dir / "settings64.bat"
    else:  # Linux/Unix
        settings = vivado_dir / "settings64.sh"
    return settings

if __name__ == "__main__":
    p = argparse.ArgumentParser(description="Run Vivado batch using settings script (cross-platform)")
    p.add_argument("--version", "-v", help="Vivado version folder name (e.g. 2023.1). If omitted, latest found is used.")
    p.add_argument("--tcl", help="Path to build.tcl (default: ./build.tcl relative to this script).")
    p.add_argument("--dry-run", action="store_true", help="Print the command instead of executing it.")
    args = p.parse_args()

    script_dir = Path(__file__).resolve().parent
    tcl = Path(args.tcl) if args.tcl else (script_dir / "build.tcl")
    if not tcl.exists():
        print(f"Error: build.tcl not found at {tcl}", file=sys.stderr)
        sys.exit(2)

    # Find Vivado installation
    root = find_vivado_root()
    try:
        vivado_dir = choose_version(root, args.version)
    except FileNotFoundError as e:
        print("Error:", e, file=sys.stderr)
        sys.exit(3)

    settings = get_settings_script(vivado_dir)
    if not settings.exists():
        script_type = "settings64.bat" if platform.system() == "Windows" else "settings64.sh"
        print(f"Error: {script_type} not found at {settings}", file=sys.stderr)
        sys.exit(4)

    cmd = build_command(settings, tcl)
    settings_script, tcl_path = cmd
    
    # Create logs directory if it doesn't exist
    logs_dir = Path.cwd() / "logs"
    logs_dir.mkdir(exist_ok=True)
    
    # Create a temporary script file to ensure environment persistence
    if platform.system() == "Windows":
        # Windows batch file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.bat', delete=False) as tmp_script:
            tmp_script.write(f'@echo off\n')
            tmp_script.write(f'call "{settings_script}"\n')
            tmp_script.write(f'vivado -mode batch -source "{tcl_path}" -log "{logs_dir}/vivado.log" -journal "{logs_dir}/vivado.jou" -tempDir "{logs_dir}"\n')
            tmp_script_path = tmp_script.name
    else:
        # Linux shell script
        with tempfile.NamedTemporaryFile(mode='w', suffix='.sh', delete=False) as tmp_script:
            tmp_script.write(f'#!/bin/bash\n')
            tmp_script.write(f'source "{settings_script}"\n')
            tmp_script.write(f'vivado -mode batch -source "{tcl_path}" -log "{logs_dir}/vivado.log" -journal "{logs_dir}/vivado.jou" -tempDir "{logs_dir}"\n')
            tmp_script_path = tmp_script.name
        # Make the shell script executable
        os.chmod(tmp_script_path, 0o755)
    
    if args.dry_run:
        print("Dry run command:")
        print(f"Platform: {platform.system()}")
        print(f"Logs directory: {logs_dir}")
        script_type = "batch file" if platform.system() == "Windows" else "shell script"
        print(f"Temporary {script_type} would contain:")
        with open(tmp_script_path, 'r') as f:
            print(f.read())
        os.unlink(tmp_script_path)  # Clean up
        sys.exit(0)

    try:
        if platform.system() == "Windows":
            rc = subprocess.run([tmp_script_path], shell=True).returncode
        else:
            # On Linux, run the shell script directly
            rc = subprocess.run([tmp_script_path]).returncode
    finally:
        # Clean up temporary file
        if os.path.exists(tmp_script_path):
            os.unlink(tmp_script_path)
    if rc != 0:
        print(f"Vivado exited with code {rc}", file=sys.stderr)
    sys.exit(rc)