<div align="center">
 <h1>Jester Anti Cheat</h1>
 <p><i>A high-accuracy anti-cheat for modern Paper servers.</i></p>
</div>

---

**Jester Anti Cheat** is a packet-based anti-cheat that **runs on Paper 1.19 –
latest** (tested on **1.21.4** and **1.21.11**) and **protects players on 1.8.9+**
through ViaVersion / ViaBackwards. It is focused on low false-positives across
mixed client versions and behind proxies, and ships a large set of movement,
combat, world, and packet checks, each fully configurable per-server.

> **Versions:** the *server* must be Paper 1.19 or newer (this is the Modrinth
> "game version" range). *Player clients* from 1.8.9 upward are supported via
> ViaVersion on that modern server.

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
(Nuker, Tower, Scaffold, VeinMiner, PacketMine) — plus original checks such as
`AimGCD` and `ReachB`, the branding/configuration, and many false-positive and
plugin-compatibility fixes.

## License

Because it is built on Grim (GPL-3.0), Jester Anti Cheat is itself licensed under
the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

You are free to use, study, modify and redistribute it under the terms of the
GPLv3. If you distribute it (modified or not), you must keep it **open-source**
under the GPLv3 and **preserve the Grim attribution above**.
