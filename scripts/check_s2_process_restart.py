"""Verify draft/cache recovery in the real app sandbox after an OS process kill.

Run from the repository root: python3 scripts/check_s2_process_restart.py DEVICE
The write-phase test runner is expected to fail when its app is killed. Only a
subsequent passing verify phase constitutes success. No app uninstall is used.
"""
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time


def main():
    device = sys.argv[1]
    root = Path(__file__).resolve().parents[1] / "loccoc_application"
    platform = "android" if device.startswith("emulator-") else "ios"
    logs = Path(tempfile.mkdtemp(prefix=f"loccoc-s2-restart-{platform}-"))
    base = ["flutter", "test", "integration_test/live_backend_s2_e2e_test.dart",
            "-d", device, "--no-uninstall", "--timeout", "150s", "--reporter", "expanded"]
    write_log = logs / "write.log"
    with write_log.open("w") as out:
        writer = subprocess.Popen(base + ["--dart-define=PROCESS_RESTART_PHASE=write",
                                  "--dart-define=HOLD_FOR_PROCESS_KILL=true"],
                                  cwd=root, stdout=out, stderr=subprocess.STDOUT,
                                  start_new_session=True)
        try:
            deadline = time.monotonic() + 240
            while "S2_RESTART_READY" not in write_log.read_text():
                if writer.poll() is not None:
                    raise RuntimeError(f"Write phase exited before durable marker: {write_log}")
                if time.monotonic() > deadline:
                    raise TimeoutError(f"No durable marker within 240 seconds: {write_log}")
                time.sleep(0.2)
            if platform == "ios":
                kill = ["xcrun", "simctl", "terminate", device, "com.example.loccocApplication"]
            else:
                kill = [str(Path.home() / "Library/Android/sdk/platform-tools/adb"),
                        "-s", device, "shell", "am", "force-stop", "com.example.loccoc_application"]
            subprocess.run(kill, check=True, timeout=20)
            print(f"{platform}: terminated real app after durable draft/cache marker", flush=True)
            try:
                writer.wait(timeout=30)
            except subprocess.TimeoutExpired:
                os.killpg(writer.pid, signal.SIGTERM)
                writer.wait(timeout=10)
        finally:
            if writer.poll() is None:
                os.killpg(writer.pid, signal.SIGTERM)
                writer.wait(timeout=10)
    verify_log = logs / "verify.log"
    with verify_log.open("w") as out:
        result = subprocess.run(base + ["--dart-define=PROCESS_RESTART_PHASE=verify"],
                                cwd=root, stdout=out, stderr=subprocess.STDOUT, timeout=240)
    if result.returncode != 0 or "All tests passed!" not in verify_log.read_text():
        raise RuntimeError(f"Recovery failed: {verify_log}")
    print(f"{platform}: process-kill recovery PASS; logs: {logs}", flush=True)


if __name__ == "__main__":
    main()
