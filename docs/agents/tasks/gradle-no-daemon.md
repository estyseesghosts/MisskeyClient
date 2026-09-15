# Objective

Agents run Gradle with --no-daemon --console=plain. Builds exit after output. No agent hangs.

# Invariants

- Preserve unrelated worktree changes in app/.
- Keep daemon available for interactive use.
- Keep Beeline product name in user content.
- Keep protocol boundaries intact.

# Decisions

- Enforce --no-daemon by instruction, not by global daemon=false.
- Add GRADLE_OPTS safety net for agent environments.
- Harden BAT wrappers with plain console and closed stdin.
- Fail fast on missing release passwords in non-interactive use.

# Completed

- Slice 1 — AGENTS.md requires --no-daemon --console=plain, safety net, timeout, closed stdin.
- Slice 2 — build-debug.bat and build-release.bat use plain console and NUL stdin. Release prompts skip under AGENT_NONINTERACTIVE or CI.

# Current slice

- None. Implementation complete. No commit created.

# Files involved

- AGENTS.md
- build-debug.bat
- build-release.bat
- docs/agents/tasks/gradle-no-daemon.md

# Verification

- gradlew.bat --no-daemon --console=plain --version exits with Gradle 9.6.0 report.
- gradlew.bat --no-daemon --console=plain :app:help exits. BUILD SUCCESSFUL in 8s. Daemon stops at end.
- build-release.bat with missing keystore exits 1 without hang.
- build-release.bat with dummy keystore and AGENT_NONINTERACTIVE=1 exits 1 with password error. No prompt hang.
- Full test assembleRelease not run. Live-server and device checks unverified.

# Next

- Commit AGENTS.md, build-debug.bat, build-release.bat, and task-state file on request.
- Run full no-daemon test assembleRelease with explicit timeout after commit.

# Blockers

- None.

# Last safe commit

- 9da86e1 Record feed repair checkpoint
