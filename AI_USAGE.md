# AI usage

What the agent actually produced, and what happened to it. `AGENTS.md` states the rules;
this file records whether they held.

Entries are appended when a review rejects or rewrites something, and when the gate
catches an error the agent had reported as done. Accepted-as-written work is not logged
individually — it is the default, and logging it would bury the interesting cases.

---

## Log

### Scaffolding — the gate caught two failures the agent had not

Both were reported as complete before they were run.

1. **Mangled escape sequences.** The agent wrote `settings.gradle.kts` through a shell
   heredoc, and the shell collapsed `\\` to `\`. Kotlin rejected `"com\.android.*"` as an
   unsupported escape sequence. The same collapse silently corrupted `local.properties`.

2. **A gate that reported success while failing.** The first gate run was piped
   (`./gradlew build … | tail`), so the shell reported the exit status of `tail`, not of
   Gradle. It printed `exit code 0` under the text `BUILD FAILED`. Fixed by setting
   `pipefail` and echoing `${PIPESTATUS[0]}` explicitly.

The second one is the more interesting failure: the verification step itself was
unsound, so a green result meant nothing. A gate is only worth what its failure
detection is worth — checking that a gate can actually fail is part of trusting it.
