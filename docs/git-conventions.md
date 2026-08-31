# Git conventions

This file describes the branch and commit conventions this project actually uses.
It is a record of the practice, not a proposal — every commit in `git log` follows it.

---

## Commit messages: Conventional Commits

```
<type>(<scope>)<!>: <subject>
```

- **type** required, lower case, see the table below
- **scope** optional — a module or feature area (`feed`, `saved`, `network`, `deps`)
- **`!`** marks a breaking change, before the colon
- **subject** imperative, lower case, no trailing period

### type

| type | used for |
|---|---|
| `feat` | new behaviour |
| `fix` | defect repair |
| `refactor` | structural change with no behavioural change |
| `test` | test-only change |
| `docs` | documentation-only change |
| `build` | build configuration, dependencies |
| `ci` | CI configuration |
| `chore` | anything else |

### The subject says *why*, not only *what*

```
❌ refactor: change FeedViewModel
✅ refactor(feed)!: derive fetching from demand and staleness
```

The second one tells a reader what changed conceptually without opening the diff.

### Trailer for AI-assisted commits

```
Assisted-by: LLM Claude Code
```

The format is the [Linux kernel's](https://docs.kernel.org/process/coding-assistants.html).
It is deliberately not `Co-authored-by:` — most projects decline to list a model as an
author, and [apache/airflow](https://raw.githubusercontent.com/apache/airflow/main/AGENTS.md)
forbids it outright. Authorship carries responsibility that stays with the human.

---

## Branch model

Short-lived topic branches, merged back into `main` with `--no-ff`. No `develop` branch.

```
main  ──●────────────●────────────●──
         \          / \          /
          ●──●──●──●   ●──●──●──●
       feat/article-feed   feat/offline-saved
```

### Branch names use the same vocabulary as commit types

```
<type>/<short description>
```

| branch | the commits it produces |
|---|---|
| `feat/article-feed` | `feat(feed): show articles in a list` |
| `refactor/feed-item-model` | `refactor(domain)!: let the feed hold items of different shapes` |
| `docs/readme` | `docs: explain the freshness reasoning` |

Sharing the prefix means the branch name and its commits corroborate each other, so
reading the history later does not require guessing what a branch was for.

| prefix | used for | lifetime |
|---|---|---|
| `spike/` | exploration, prototypes, design comparison | **may never be merged** — discard once the question is answered |

`spike/` is deliberately separate from the rest: its output is expected to be
*knowledge*, not code. Keeping it out of `feat/` stops throwaway work from
polluting the history.

### Why `--no-ff`

A fast-forward erases the fact that a topic branch existed; the history flattens into
a line and you can no longer tell which commits belonged to one piece of work.
`--no-ff` keeps the merge commit, so `git log --graph` shows each topic's extent.

```bash
git merge --no-ff feat/article-feed
```

---

## One commit, one behaviour

Each commit should be independently reviewable and independently revertable.

A consequence of a change belongs in its *own* commit rather than folded into the
change that caused it — separating them lets the history say "because fetching became
demand-driven, this class no longer had a reason to exist."

---

## Before merging

Every commit on `main` should build with tests green. Locally:

```bash
./gradlew build detekt lint
```

`allWarningsAsErrors = true`, so a compiler warning fails the build.
CI runs the same gate on push and on pull requests — see
[`AGENTS.md`](../AGENTS.md) for why that, and not this file, is the enforcing layer.
