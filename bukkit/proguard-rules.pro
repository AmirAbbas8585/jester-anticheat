# ──────────────────────────────────────────────────────────────────────────────
# Jester Anti Cheat — ProGuard rules for the PUBLIC (Modrinth) obfuscated build.
#
# Goal: rename our own internal classes / methods / fields so the distributed jar
# cannot be trivially decompiled back into readable source (keeping the source
# private), WITHOUT breaking Bukkit / PacketEvents / cloud reflection.
#
# Scope: ONLY our own code under ac.jester.anticheat.** is obfuscated. Every
# bundled library (including the relocated ac.jester.anticheat.shaded.**
# packages: cloud, adventure, snakeyaml, gson, HikariCP, ...) is kept as-is —
# those are open source already and are reflection-heavy, so renaming them is
# pointless and risky.
#
# ⚠ TEST THE OBFUSCATED JAR ON A REAL SERVER BEFORE PUBLISHING. Obfuscation can
#   break anything that resolves a class/method/field by string name. If a
#   feature breaks, add a -keep rule for the class involved and rebuild.
# ──────────────────────────────────────────────────────────────────────────────

# Optimization is the most breakage-prone ProGuard stage; we only want renaming.
-dontoptimize
# shadowJar already runs minimize(); don't let ProGuard strip reflectively-used
# code on top of that.
-dontshrink
# Strip debug info (this is what actually keeps the source private): no source
# file names, no line numbers, no local variable names in the output.
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable
# But keep what reflection frameworks need: annotations, generic signatures,
# inner-class links, parameter names (cloud resolves command args by name).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters
-keepparameternames
# Soft-dependencies (ItemsAdder, GSit, DeluxeCombat, AuraSkills, WorldGuard,
# LPX, ...) are not on the build classpath, so references to them won't resolve.
-dontwarn **
-ignorewarnings

# ── Keep everything that is NOT our own source ────────────────────────────────
# Every non-ac.jester.anticheat class (JDK refs, any non-relocated lib) stays.
-keep class !ac.jester.anticheat.** { *; }

# Relocated/shaded third-party libraries must NOT be renamed (reflection-heavy).
# Listed explicitly so that the relocated engine under shaded.core.** is the ONE
# shaded package left obfuscatable — that renames the engine's class names (which
# still contained the upstream brand) so they don't appear in the public jar.
-keep class ac.jester.anticheat.shaded.configuralize.** { *; }
-keep class ac.jester.anticheat.shaded.com.** { *; }
-keep class ac.jester.anticheat.shaded.gson.** { *; }
-keep class ac.jester.anticheat.shaded.maps.** { *; }
-keep class ac.jester.anticheat.shaded.fastutil.** { *; }
-keep class ac.jester.anticheat.shaded.okhttp3.** { *; }
-keep class ac.jester.anticheat.shaded.okio.** { *; }
-keep class ac.jester.anticheat.shaded.snakeyaml.** { *; }
-keep class ac.jester.anticheat.shaded.json.** { *; }
-keep class ac.jester.anticheat.shaded.intellij.** { *; }
-keep class ac.jester.anticheat.shaded.jetbrains.** { *; }
-keep class ac.jester.anticheat.shaded.incendo.** { *; }
-keep class ac.jester.anticheat.shaded.geantyref.** { *; }
-keep class ac.jester.anticheat.shaded.zaxxer.** { *; }

# The relocated engine (shaded.core.**) IS obfuscated. Adapt anything that
# resolves those classes by string/resource so reflection & service loading keep
# working after the rename: Class.forName() literals, service-file names and
# their contents.
-adaptclassstrings ac.jester.anticheat.shaded.core.**
-adaptresourcefilenames META-INF/services/**
-adaptresourcefilecontents META-INF/services/**

# ── Entry points inside our own code (reflection / server contracts) ──────────
# The Bukkit plugin main class is named in plugin.yml and instantiated by the
# server — its name and lifecycle methods must survive.
-keep class ac.jester.anticheat.platform.bukkit.JesterAntiCheatPlugin { *; }

# Bukkit dispatches events to @EventHandler methods reflectively.
-keepclassmembers class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler <methods>;
}

# Enum constants are matched by name during config / protocol deserialization.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# cloud command framework discovers and invokes our command handlers via
# annotations/reflection — keep the command tree intact.
-keep class ac.jester.anticheat.command.** { *; }

# Check base type: checks are instantiated programmatically and read for their
# @CheckData annotation; keep the public surface so nothing dispatches into a
# renamed-away member.
-keep class ac.jester.anticheat.checks.Check { *; }
-keep @interface ac.jester.anticheat.checks.CheckData { *; }
