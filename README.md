# CaseManager

Advanced moderation case management for Minecraft servers.

## Features

- Moderation case management
- Supabase PostgreSQL database
- Staff investigators
- Evidence management
- Case timeline
- CoreProtect integration
- Vulcan integration
- LiteBans integration
- Discord integration
- Discord Components V2 interface
- Onyx-themed Discord UI
- Configurable permissions

## Supported Platforms

CaseManager targets the Spigot API and is designed to work with:

- Spigot
- Paper
- Purpur

## Requirements

- Java 21+
- A Supabase project with PostgreSQL
- Spigot/Paper/Purpur server

## Supabase Database Setup

1. Create a Supabase project.
2. Open the Supabase Dashboard.
3. Open **Connect**.
4. Choose **Session pooler**.
5. Copy the pooler host, port, database name, username, and password into `plugin/src/main/resources/config.yml`.
6. Keep `database.ssl: true`.
7. The CaseManager plugin creates its required tables automatically when it starts.

The Session Pooler is used because it is IPv4-compatible and is suitable when the Minecraft server cannot use a direct IPv6 database connection.

Do not commit a real Supabase database password to GitHub.

## Database Architecture

CaseManager uses PostgreSQL rather than MySQL. The JDBC driver and HikariCP are packaged into the final shaded plugin JAR.

The database stores:

- `cases`
- `case_notes`
- `case_evidence`
- `case_investigators`
- `case_timeline`

Future Discord permission tables will use the same PostgreSQL database.

## Development

The Minecraft plugin is located in:

`/plugin`

## License

License information will be added before the first release.
