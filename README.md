# NeoBackup

> **This project is 100% AI-generated with zero human-written code.**

A multi-platform backup solution for Minecraft servers. Supports both **NeoForge 1.21** and **Paper 26.2**.

## Features

- Crontab-based scheduled backups
- Skip backup if no players have joined since last backup
- Supports zstd and zip compression
- Auto-delete old backups by count or total size
- Manual force backup command: `/neobackup backup`

## Project Structure

```
common/          Shared logic (config, backup, compression, scheduling)
neoforge/        NeoForge 1.21 mod  →  mods/
paper/           Paper 26.2 plugin  →  plugins/
```

## Configuration

### NeoForge

Config file: `config/neobackup-common.toml`

### Paper

Config file: `plugins/NeoBackup-Paper/config.yml`

### Options

- `cronExpression`: Crontab expression for backup schedule (default: `0 2 * * *`)
- `compressionMethod`: `zstd` or `zip` (default: `zip`)
- `backupPath`: Backup storage path (default: `./backups`)
- `skipIfNoPlayers`: Skip backup if no players since last backup (default: `true`)
- `maxBackupCount`: Maximum backups to keep, 0 to disable (default: `0`)
- `maxBackupSizeGB`: Maximum total backup size in GB, 0 to disable (default: `0`)

## Commands

| Command | Description | Permission |
|---|---|---|
| `/neobackup backup` | Manual backup | OP level 2 / `neobackup.backup` |
| `/neobackup reload` | Reload config | OP level 2 / `neobackup.reload` |

## Installation

### NeoForge
1. Download `NeoBackup-NeoForge-*.jar` from Releases
2. Place in server's `mods/` folder
3. Start server
4. *(Optional, for zstd with Java25)* Add to JVM start args: `--enable-native-access=com.github.luben.zstd_jni`

### Paper
1. Download `NeoBackup-Paper-*.jar` from Releases
2. Place in server's `plugins/` folder
3. Start server
4. *(Optional, for zstd with Java25)* Add to JVM start args: `--enable-native-access=com.github.luben.zstd_jni`

## Building

Requirements: JDK 25 (SDKMAN recommended)

```bash
# Full build (both variants)
./gradlew build

# NeoForge only
./gradlew :neoforge:build

# Paper only
./gradlew :paper:shadowJar
```

Build outputs:
- `neoforge/build/libs/NeoBackup-NeoForge-*.jar`
- `paper/build/libs/NeoBackup-Paper-*.jar`

## Testing

```bash
# Run common module unit tests
./gradlew :common:test
```



## Requirements

### NeoForge
- Minecraft 1.21
- NeoForge 21.0.167+
- Java 21

### Paper
- Paper 26.2+
- Java 25

---

*Built entirely by AI. Zero human code. Full human creativity.*
