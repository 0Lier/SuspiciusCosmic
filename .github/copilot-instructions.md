# Copilot instructions for DifficultyCosmic

Purpose: quick reference for Copilot sessions to understand how to build, run, and reason about this repository.

---

## Build, test, and lint commands
- Build (fat plugin JAR):
  - ./gradlew build
  - ./gradlew shadowJar        # produces the all-in-one jar
  - Output: build/libs/DifficultyCosmic-<version>-all.jar
- Run a development Paper server (fast feedback):
  - ./gradlew runServer       # configured in build.gradle (minecraftVersion 26.2)
- Tests:
  - Full test suite: ./gradlew test
  - Single test by name: ./gradlew test --tests "com.cosmicraft.difficultycosmic.MyTest"
  - Single test class: ./gradlew test --tests "com.cosmicraft.difficultycosmic.*Test"
- Linting / checks: none configured explicitly. Use ./gradlew check if custom checks are added.

Notes:
- Java toolchain configured in build.gradle (javaVersion = 25). gradle.properties contains a JAVA home entry used in CI/local setups.

---

## High-level architecture (big picture)
- Type: Minecraft Paper plugin (Java), package `com.cosmicraft.difficultycosmic`.
- Entry point: `com.cosmicraft.difficultycosmic.DifficultyCosmic` (main plugin class declared in src/main/resources/plugin.yml).
  - onEnable(): duplicates spawn limits for loaded worlds and registers event listeners and commands.
  - Exposes helper `doubleSpawnLimits(World)` used at startup.
- Structure:
  - Listeners (many classes named *Listener) handle CreatureSpawn and other events to apply "Difficulty Cosmic" behavior.
  - Commands: `DifficultyCommand`, `DebugSpawnCommand` — debug command can force-effect application for testing.
  - Resources: `plugin.yml` defines plugin metadata, commands, permissions, and is processed during resources to inject the version.
- Build/runtime integration:
  - Shadow plugin used to produce an all-in-one jar suitable for placing into a Paper server's plugins/ directory.
  - run-paper Gradle plugin is present to run a local Paper server for iteration (./gradlew runServer).

---

## Key conventions specific to this codebase
- Naming:
  - Event handlers are named `*Listener` and live under `src/main/java/com/cosmicraft/difficultycosmic/`.
  - Commands are classes implementing executors (e.g., `DebugSpawnCommand`).
- Metadata & identification:
  - Entities spawned by the plugin are tagged with metadata key `"dc_spawned"` to identify plugin-generated mobs.
- Event priorities and scheduling:
  - Some events use `EventPriority.MONITOR` and `ignoreCancelled = true` for post-processing/observability.
  - When spawning or modifying entities as a follow-up action, code uses `plugin.getServer().getScheduler().runTask(plugin, ...)` to schedule tasks on the main thread.
- Public helper methods for debug:
  - Certain listeners expose public methods (e.g., `applySpiderEffects`) so the debug command can force behaviors deterministically.
- Logging & i18n:
  - Log lines are prefixed with `[DifficultyCosmic]` in code.
  - Source comments and user-facing strings are in Spanish; match that style when adding new messages.
- plugin.yml is filtered at build time (processResources expands the `version` property from Gradle). Keep keys and command names consistent with code (`getCommand("dificultad")`, etc.).

---

## Files and CI hooks checked for AI assistant configs
Checked for common assistant/agent files and rules and incorporated findings (none present):
- CLAUDE.md, .cursorrules, .cursor/, AGENTS.md, .windsurfrules, CONVENTIONS.md, AIDER_CONVENTIONS.md, .clinerules — none found in repository root.

---

If updates are made to build tooling (Gradle tasks, Java version, or artifact names), update this file so Copilot sessions see the current commands and CI expectations.

Summary: added concise build/run/test instructions, a high-level architecture summary focused on cross-file relationships, and repository-specific conventions Copilot should follow when generating or modifying code.
