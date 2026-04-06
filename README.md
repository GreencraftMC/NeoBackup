# NeoBackup

A server-only backup mod for NeoForge 1.21.1

## Features

- Crontab-based scheduled backups
- Skip backup if no players have joined since last backup
- Supports zstd and zip compression
- Auto-delete old backups by count or total size
- Manual force backup command: `/neobackup backup`

## Configuration

The mod creates a config file at `config/neobackup-common.toml` with the following options:

- `cronExpression`: Crontab expression for backup schedule (default: `0 2 * * *` for daily at 2 AM)
- `compressionMethod`: Compression method (`zstd` or `zip`, default: `zip`)
- `backupPath`: Backup storage path (default: `./backups`). Supported formats:
  - `./backups` - Relative to server directory
  - `../backups` - Parent directory
  - `C:\\backups` - Absolute Windows path
  - `C:\\backups\\minecraft` - Absolute Windows path with subfolder
- `skipIfNoPlayers`: Skip backup if no players have joined since last backup (default: `true`)
- `maxBackupCount`: Maximum number of backups to keep, 0 to disable (default: `0`)
- `maxBackupSizeGB`: Maximum total backup directory size in GB, 0 to disable (default: `0`). **Size limit is applied first.**

## Commands

- `/neobackup backup` - Manually backup (requires OP level 2)
- `/neobackup reload` - Reload configuration

## Installation

1. Download the latest release from the releases page
2. Place the JAR file in your server's `mods` folder
3. Start or restart your server

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Requirements

- NeoForge 21.0.167 or later
- Minecraft 1.21
- Java 21

---

> **This project is 100% AI-generated code with zero human-written code.**
