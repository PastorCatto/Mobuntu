#!/usr/bin/env python3
"""
Mobuntu Devkit — auto-runner.

Detects which Mobuntu variant lives in the current directory tree (or repo
root, walking up) and dispatches to its build pipeline. Provides a curses
TUI with a regedit-style split-pane layout: variant tree on the left,
status / log / actions on the right.

Variants are auto-detected by looking for a ``build.env`` and ``build.sh``
inside known folder names:

    Mobuntu/         — SDM845 main branch
    Mobuntu-PDK/     — Ubuntu PDK adaptation
    Mobuntu-L4T/     — Switchroot L4T target

Run from the repo root (or any ancestor):

    python3 devkit.py

ASCII-only — no emoji.
"""
from __future__ import annotations

import curses
import os
import subprocess
import sys
import threading
import time
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path
from typing import Deque, List, Optional

# -----------------------------------------------------------------------------
# Variant discovery
# -----------------------------------------------------------------------------

KNOWN_VARIANTS = ("Mobuntu", "Mobuntu-PDK", "Mobuntu-L4T", "Mobuntu-PS4")


@dataclass
class Variant:
    name: str
    path: Path
    build_env: Path
    build_sh: Path
    has_devkit_meta: bool = False
    config: dict = field(default_factory=dict)

    @property
    def label(self) -> str:
        suite = self.config.get("UBUNTU_SUITE", "?")
        release = self.config.get("RELEASE_TAG", "?")
        return f"{self.name:<14}  suite={suite:<6}  release={release}"


def find_repo_root(start: Path) -> Path:
    """Walk up looking for a .git or any KNOWN_VARIANTS sibling."""
    cur = start.resolve()
    for parent in [cur] + list(cur.parents):
        if (parent / ".git").exists():
            return parent
        for v in KNOWN_VARIANTS:
            if (parent / v).is_dir():
                return parent
    return cur


_VAR_DEFAULT_RE = __import__("re").compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*):-([^}]*)\}")


def parse_build_env(path: Path) -> dict:
    """Best-effort parse of shell var assignments from a build.env.

    Handles patterns like:
        FOO="bar"
        FOO="${FOO:-bar}"        # inline comment
        export FOO=bar
    """
    out: dict = {}
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return out
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[len("export "):]
        key, _, val = line.partition("=")
        key = key.strip()
        if not key.isidentifier():
            continue

        val = val.lstrip()
        # If value starts with a quote, take everything up to the matching
        # closing quote; otherwise, take everything up to the first
        # whitespace-or-#.
        if val.startswith('"') or val.startswith("'"):
            quote = val[0]
            end = val.find(quote, 1)
            val = val[1:end] if end != -1 else val[1:]
        else:
            # strip inline comment
            for sep in ("  #", "\t#", " #"):
                idx = val.find(sep)
                if idx != -1:
                    val = val[:idx]
                    break
            val = val.rstrip()

        # Resolve ${VAR:-default} -> default (one-pass; nested not supported).
        match = _VAR_DEFAULT_RE.fullmatch(val)
        if match is not None:
            val = match.group(2)

        out[key] = val
    return out


def discover_variants(root: Path) -> List[Variant]:
    found: List[Variant] = []
    for name in KNOWN_VARIANTS:
        vpath = root / name
        env = vpath / "build.env"
        sh = vpath / "build.sh"
        if vpath.is_dir() and env.is_file() and sh.is_file():
            cfg = parse_build_env(env)
            found.append(Variant(
                name=name,
                path=vpath,
                build_env=env,
                build_sh=sh,
                has_devkit_meta=(vpath / ".devkit").exists(),
                config=cfg,
            ))
    return found


# -----------------------------------------------------------------------------
# Build runner — captures live output for the right pane.
# -----------------------------------------------------------------------------

class BuildRunner:
    def __init__(self, max_lines: int = 5000) -> None:
        self.lines: Deque[str] = deque(maxlen=max_lines)
        self.proc: Optional[subprocess.Popen] = None
        self.thread: Optional[threading.Thread] = None
        self.lock = threading.Lock()
        self.exit_code: Optional[int] = None
        self.running = False

    def is_running(self) -> bool:
        return self.running

    def append(self, line: str) -> None:
        with self.lock:
            self.lines.append(line)

    def snapshot(self) -> List[str]:
        with self.lock:
            return list(self.lines)

    def clear(self) -> None:
        with self.lock:
            self.lines.clear()
            self.exit_code = None

    def start(self, cwd: Path, cmd: List[str], env_extra: Optional[dict] = None) -> None:
        if self.running:
            self.append("[devkit] A build is already running. Cancel it first.")
            return
        self.clear()
        self.append(f"[devkit] cd {cwd}")
        self.append(f"[devkit] exec: {' '.join(cmd)}")
        env = os.environ.copy()
        if env_extra:
            env.update(env_extra)
        try:
            self.proc = subprocess.Popen(
                cmd,
                cwd=str(cwd),
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                bufsize=1,
                universal_newlines=True,
                env=env,
            )
        except OSError as exc:
            self.append(f"[devkit ERROR] {exc}")
            self.running = False
            self.exit_code = 127
            return
        self.running = True
        self.thread = threading.Thread(target=self._reader, daemon=True)
        self.thread.start()

    def _reader(self) -> None:
        assert self.proc is not None and self.proc.stdout is not None
        for line in self.proc.stdout:
            self.append(line.rstrip("\n"))
        self.proc.wait()
        self.exit_code = self.proc.returncode
        self.append(f"[devkit] process exited with code {self.exit_code}")
        self.running = False

    def cancel(self) -> None:
        if self.running and self.proc is not None:
            self.append("[devkit] sending SIGTERM")
            try:
                self.proc.terminate()
            except OSError:
                pass


# -----------------------------------------------------------------------------
# TUI
# -----------------------------------------------------------------------------

ACTIONS = [
    ("b", "Build (full pipeline)",      "build_full"),
    ("s", "Build single stage...",      "build_stage"),
    ("c", "Clean build/ directory",     "clean"),
    ("e", "Edit build.env (in $EDITOR)","edit_env"),
    ("v", "View build.env",             "view_env"),
    ("k", "Cancel running build",       "cancel"),
    ("r", "Refresh variant list",       "refresh"),
    ("q", "Quit",                       "quit"),
]


def safe_addstr(win, y: int, x: int, text: str, attr: int = 0) -> None:
    """addstr that won't blow up on edge writes or non-ascii."""
    try:
        max_y, max_x = win.getmaxyx()
        if y >= max_y or x >= max_x:
            return
        text = text.encode("ascii", "replace").decode("ascii")
        win.addnstr(y, x, text, max_x - x - 1, attr)
    except curses.error:
        pass


class DevkitTUI:
    def __init__(self, stdscr, root: Path, variants: List[Variant]) -> None:
        self.stdscr = stdscr
        self.root = root
        self.variants = variants
        self.selected = 0
        self.runner = BuildRunner()
        self.scroll = 0
        self.status = f"Repo root: {root}"
        self.last_action_msg = ""

    # -- input ----------------------------------------------------------------

    def prompt_input(self, prompt: str) -> str:
        max_y, max_x = self.stdscr.getmaxyx()
        win = curses.newwin(3, max_x - 4, max_y // 2 - 1, 2)
        win.box()
        safe_addstr(win, 0, 2, f" {prompt} ", curses.A_REVERSE)
        win.refresh()
        curses.echo()
        curses.curs_set(1)
        try:
            raw = win.getstr(1, 2, max_x - 8)
        except curses.error:
            raw = b""
        curses.noecho()
        curses.curs_set(0)
        return raw.decode("utf-8", "replace").strip()

    # -- actions --------------------------------------------------------------

    def current(self) -> Optional[Variant]:
        if not self.variants:
            return None
        return self.variants[self.selected]

    def action_build_full(self) -> None:
        v = self.current()
        if v is None:
            self.last_action_msg = "No variant selected"
            return
        self.runner.start(cwd=v.path, cmd=["sudo", "bash", "./build.sh"])
        self.last_action_msg = f"Build started: {v.name}"

    def action_build_stage(self) -> None:
        v = self.current()
        if v is None:
            return
        stages = self.prompt_input("Stages (e.g. '01 02' or '04 05')")
        if not stages:
            self.last_action_msg = "Cancelled (no stages given)"
            return
        self.runner.start(
            cwd=v.path,
            cmd=["sudo", "-E", "bash", "./build.sh"],
            env_extra={"STAGES": stages},
        )
        self.last_action_msg = f"Build started ({v.name}, stages={stages})"

    def action_clean(self) -> None:
        v = self.current()
        if v is None:
            return
        confirm = self.prompt_input(f"Type DELETE to wipe {v.path}/build/")
        if confirm != "DELETE":
            self.last_action_msg = "Clean cancelled"
            return
        self.runner.start(cwd=v.path, cmd=["sudo", "rm", "-rf", "build"])
        self.last_action_msg = f"Cleaning {v.name}/build/"

    def action_view_env(self) -> None:
        v = self.current()
        if v is None:
            return
        try:
            text = v.build_env.read_text()
        except OSError as exc:
            self.last_action_msg = f"Cannot read: {exc}"
            return
        self.runner.clear()
        self.runner.append(f"[devkit] view {v.build_env}")
        for line in text.splitlines():
            self.runner.append(line)
        self.last_action_msg = "Loaded build.env"

    def action_edit_env(self) -> None:
        v = self.current()
        if v is None:
            return
        editor = os.environ.get("EDITOR", "nano")
        curses.endwin()
        try:
            subprocess.call([editor, str(v.build_env)])
        finally:
            self.stdscr.refresh()
            curses.curs_set(0)
        # Reparse
        v.config = parse_build_env(v.build_env)
        self.last_action_msg = "Edited build.env (config reloaded)"

    def action_cancel(self) -> None:
        if not self.runner.is_running():
            self.last_action_msg = "No build running"
            return
        self.runner.cancel()
        self.last_action_msg = "Cancel signal sent"

    def action_refresh(self) -> None:
        self.variants = discover_variants(self.root)
        if self.selected >= len(self.variants):
            self.selected = max(0, len(self.variants) - 1)
        self.last_action_msg = f"Refreshed ({len(self.variants)} variants)"

    # -- drawing --------------------------------------------------------------

    def draw_header(self) -> None:
        max_y, max_x = self.stdscr.getmaxyx()
        title = " Mobuntu Devkit  --  auto-runner "
        safe_addstr(self.stdscr, 0, 0, " " * (max_x - 1), curses.A_REVERSE)
        safe_addstr(self.stdscr, 0, 2, title, curses.A_REVERSE | curses.A_BOLD)
        safe_addstr(self.stdscr, 0, max_x - len(self.status) - 3, self.status,
                    curses.A_REVERSE)

    def draw_left(self, y0: int, h: int, w: int) -> None:
        # Variant tree
        safe_addstr(self.stdscr, y0, 1, "[ Variants ]", curses.A_BOLD)
        if not self.variants:
            safe_addstr(self.stdscr, y0 + 2, 2,
                        "No Mobuntu variants found.")
            safe_addstr(self.stdscr, y0 + 3, 2,
                        f"Searched: {self.root}")
            safe_addstr(self.stdscr, y0 + 5, 2,
                        "Expected one of:")
            for i, name in enumerate(KNOWN_VARIANTS):
                safe_addstr(self.stdscr, y0 + 6 + i, 4, f"- {name}/")
            return

        for i, v in enumerate(self.variants):
            attr = curses.A_REVERSE if i == self.selected else 0
            line = v.label
            safe_addstr(self.stdscr, y0 + 2 + i * 2, 2, line.ljust(w - 4), attr)
            sub = f"  -> {v.path}"
            safe_addstr(self.stdscr, y0 + 3 + i * 2, 2, sub.ljust(w - 4),
                        curses.A_DIM)

        # Actions block
        ay = y0 + 2 + len(self.variants) * 2 + 2
        safe_addstr(self.stdscr, ay, 1, "[ Actions ]", curses.A_BOLD)
        for i, (key, label, _) in enumerate(ACTIONS):
            safe_addstr(self.stdscr, ay + 2 + i, 2, f" {key}  {label}")

    def draw_right(self, y0: int, x0: int, h: int, w: int) -> None:
        # Title bar
        v = self.current()
        title = "[ Build Output ]"
        if v is not None:
            title = f"[ {v.name}  --  build output ]"
        safe_addstr(self.stdscr, y0, x0 + 1, title, curses.A_BOLD)

        # Running indicator
        if self.runner.is_running():
            ind = "  [ RUNNING ]"
            safe_addstr(self.stdscr, y0, x0 + w - len(ind) - 2, ind,
                        curses.A_BOLD | curses.A_BLINK)
        elif self.runner.exit_code is not None:
            tag = "[ OK ]" if self.runner.exit_code == 0 else f"[ FAIL {self.runner.exit_code} ]"
            attr = curses.A_BOLD
            safe_addstr(self.stdscr, y0, x0 + w - len(tag) - 2, tag, attr)

        lines = self.runner.snapshot()
        max_visible = h - 4
        if len(lines) > max_visible:
            lines = lines[-max_visible:]
        for i, line in enumerate(lines):
            safe_addstr(self.stdscr, y0 + 2 + i, x0 + 2, line)

    def draw_footer(self) -> None:
        max_y, max_x = self.stdscr.getmaxyx()
        msg = self.last_action_msg or "UP/DOWN: select  -  letter keys: action  -  q: quit"
        safe_addstr(self.stdscr, max_y - 1, 0, " " * (max_x - 1),
                    curses.A_REVERSE)
        safe_addstr(self.stdscr, max_y - 1, 2, msg,
                    curses.A_REVERSE)

    def draw(self) -> None:
        self.stdscr.erase()
        max_y, max_x = self.stdscr.getmaxyx()
        if max_y < 12 or max_x < 70:
            safe_addstr(self.stdscr, 0, 0,
                        "Terminal too small (need >= 70x12). Resize and press any key.")
            self.stdscr.refresh()
            return

        self.draw_header()

        # Split panes: left ~38%, right rest, with vertical separator
        left_w = max(28, int(max_x * 0.38))
        right_x = left_w + 1
        right_w = max_x - right_x
        body_y = 2
        body_h = max_y - body_y - 1

        # Left pane border
        for y in range(body_y, body_y + body_h):
            safe_addstr(self.stdscr, y, left_w, "|", curses.A_DIM)

        self.draw_left(body_y, body_h, left_w)
        self.draw_right(body_y, right_x, body_h, right_w)
        self.draw_footer()
        self.stdscr.refresh()

    # -- main loop ------------------------------------------------------------

    def loop(self) -> None:
        curses.curs_set(0)
        self.stdscr.nodelay(True)
        self.stdscr.timeout(150)

        action_map = {key: name for key, _, name in ACTIONS}

        while True:
            self.draw()
            try:
                ch = self.stdscr.getch()
            except KeyboardInterrupt:
                break
            if ch == -1:
                continue
            if ch in (curses.KEY_UP, ord("k")):
                if self.variants:
                    self.selected = (self.selected - 1) % len(self.variants)
            elif ch in (curses.KEY_DOWN, ord("j")):
                if self.variants:
                    self.selected = (self.selected + 1) % len(self.variants)
            elif ch in (ord("q"), ord("Q")):
                if self.runner.is_running():
                    confirm = self.prompt_input("Build is running. Cancel and quit? (y/N)")
                    if confirm.lower() != "y":
                        continue
                    self.runner.cancel()
                break
            elif 0 < ch < 256:
                key = chr(ch).lower()
                act = action_map.get(key)
                if act is None:
                    continue
                handler = getattr(self, f"action_{act}", None)
                if handler is not None:
                    handler()
                # tiny pause so the user sees the "started" status
                time.sleep(0.05)


# -----------------------------------------------------------------------------
# Entrypoint
# -----------------------------------------------------------------------------

def main() -> int:
    here = Path(os.getcwd())
    root = find_repo_root(here)
    variants = discover_variants(root)

    if "--list" in sys.argv:
        print(f"Repo root: {root}")
        if not variants:
            print("No variants found.")
            return 1
        for v in variants:
            print(f"  {v.name:<14}  {v.path}")
            for k in ("UBUNTU_SUITE", "FLAVOR", "L4T_RELEASE", "RELEASE_TAG"):
                if k in v.config:
                    print(f"    {k}={v.config[k]}")
        return 0

    if "--build" in sys.argv:
        # Headless build: --build <variant_name> [stages...]
        idx = sys.argv.index("--build")
        try:
            target = sys.argv[idx + 1]
        except IndexError:
            print("Usage: devkit.py --build <variant_name> [STAGES='01 02']")
            return 2
        match = next((v for v in variants if v.name == target), None)
        if match is None:
            print(f"No variant named {target}. Available: {[v.name for v in variants]}")
            return 2
        env = os.environ.copy()
        cmd = ["sudo", "-E", "bash", "./build.sh"]
        rc = subprocess.call(cmd, cwd=str(match.path), env=env)
        return rc

    try:
        return curses.wrapper(lambda stdscr: DevkitTUI(stdscr, root, variants).loop()) or 0
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
