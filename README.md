<div align="center">
 <h1>Jester Anti Cheat</h1>
 <p><i>A high-accuracy anti-cheat for modern Paper servers.</i></p>
</div>

---

**Jester Anti Cheat** is a packet-based anti-cheat for Paper **1.21.4**, focused on
low false-positives across mixed client versions (ViaBackwards / 1.8+) and behind
proxies. It ships a large set of movement, combat, world, and packet checks, each
fully configurable per-server.

> `jester` is the umbrella brand for a family of plugins; this repository is the
> anti-cheat (**Jester Anti Cheat**).

## Highlights

- **Per-check configuration** — every check has its own `enabled`, `punishable`,
  `max-violations`, alert and punishment settings in `config.yml`.
- **Tuned for real servers** — generous grace windows for proxy/version-translation
  jitter, ping, low-TPS dips, and common false-positive sources.
- **Cancel-before-kick** for auto-clicker checks, and a punish-grace window so a
  one-off burst never instantly kicks a legit player.
- **Database logging** (SQLite / MySQL) of violations, punishments and profiles.
- **In-game tooling** — alerts with rich hover info, violation history, freeze,
  spectate, stats, and more under `/jester`.

## Building

Requires JDK 21.

```bash
# normal build (readable jar)
./gradlew :bukkit:shadowJar

# obfuscated build (private distribution)
./gradlew :bukkit:proguardJar -Pobfuscate=true
```

Artifacts are written to `bukkit/build/libs/`.

## Commands

All commands live under `/jester` (alias `/jac`). Run `/jester` for the full list.

## License

Private project — all rights reserved. Not for redistribution.
