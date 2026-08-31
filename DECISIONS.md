# Decisions

A log of the choices a reviewer would reasonably question. Each entry records what was
picked, what was considered instead, and what the trade-off costs.

Entries are written **at the moment of the decision**, in the same commit that makes it.
Reconstructing "what did I consider?" afterwards produces a tidier story than the true
one — the roads not taken are exactly what memory drops first.

---

## 1. Seven modules rather than one

**Picked** — `:app`, `:core:domain`, `:core:data`, `:core:ui`, and one module per screen
(`:feature:feed`, `:feature:detail`, `:feature:saved`).

**Considered instead** — a single `:app` module with packages. For an app this size that
is a defensible choice and builds faster.

**Trade-off** — module boundaries cost build configuration and some ceremony, and the
build is slower than a single module would be. What they buy is that the dependency
direction is enforced by the compiler rather than by discipline: `:core:domain` is a
plain Kotlin module with no Android dependency, so a DTO or a Compose type *cannot*
leak into it — the build fails. Packages only make that a convention.

**Why it was decided first** — module boundaries are the one decision that gets more
expensive the longer it is deferred, because every file written before it has to move.
