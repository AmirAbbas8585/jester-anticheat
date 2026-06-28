<div align="center">
 <h1>Jester Anti Cheat</h1>
 <p><i>A high-accuracy anti-cheat for modern Paper servers.</i></p>
</div>

---

**Jester Anti Cheat** is a packet-based anti-cheat built on the Grim engine, so
it runs on the **same server range as Grim** — Bukkit / Spigot / Paper / Purpur /
Folia, **1.8.x through latest** (`api-version 1.13`, tested by us on **1.21.4**
and **1.21.11**). It is focused on low false-positives across mixed client
versions and behind proxies, and ships a large set of movement, combat, world,
and packet checks, each fully configurable per-server.

> **Versions:** the load range matches upstream Grim (`api-version 1.13`); older
> clients are additionally covered via ViaVersion. We test primarily on current
> 1.21.x; the wide range comes from the Grim engine.

> `jester` is the umbrella brand for a family of plugins; this repository is the
> anti-cheat (**Jester Anti Cheat**).

## Requirements

- **[PacketEvents](https://modrinth.com/plugin/packetevents) — required.** The
  plugin will not enable without it.
- **ViaVersion + ViaBackwards** *(optional)* — only needed to protect players on
  client versions older than your server.

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
# normal build (this is the release jar)
./gradlew :bukkit:shadowJar
```

Artifacts are written to `bukkit/build/libs/`.

## Commands

All commands live under `/jester` (alias `/jac`). Run `/jester` for the full list.

## Credits

Jester Anti Cheat is built on the **Grim** anti-cheat engine and uses the Grim
API (`ac.grim.grimac`) as its packet/movement-prediction core.

- Grim AntiCheat — https://github.com/GrimAnticheat/Grim (GPL-3.0)

This is a **modified, rebranded** distribution built on that engine. On top of
Grim's movement-prediction core, this project ships a large suite of checks that
are **not part of upstream Grim** — roughly **40+ extra checks** spanning combat
(KillAura, Reach, TriggerBot, Criticals, Crystal/Anchor/Bed aura), inventory &
auto-* (AutoClicker, AutoTotem, AutoArmor, AutoPot, ChestStealer, …), and world
(Nuker, Tower, Scaffold, VeinMiner, PacketMine) — plus an original `ReachB`
check, the branding/configuration, and many false-positive and
plugin-compatibility fixes.

Parts of the development (refactoring, new checks, tuning) were done with the help
of **Claude** (Anthropic) as a coding assistant.

## License

Because it is built on Grim (GPL-3.0), Jester Anti Cheat is itself licensed under
the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

You are free to use, study, modify and redistribute it under the terms of the
GPLv3. If you distribute it (modified or not), you must keep it **open-source**
under the GPLv3 and **preserve the Grim attribution above**.
