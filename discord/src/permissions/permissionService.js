const config = require("../config");
const database = require("../database/database");

class PermissionService {

    constructor() {

        this.cache = new Map();

        this.cacheLifetime =
            60 * 1000;
    }

    async getSettings(guildId) {

        if (!guildId) {

            return this.getEnvironmentDefaults();
        }

        const cached =
            this.cache.get(guildId);

        if (
            cached &&
            Date.now() - cached.loadedAt <
                this.cacheLifetime
        ) {

            return cached.settings;
        }

        const result =
            await database.query(
                `
                SELECT
                    guild_id,
                    permission_mode,
                    global_role_id,
                    role_case,
                    role_evidence,
                    role_timeline,
                    role_investigators,
                    role_status,
                    role_settings,
                    role_permissions
                FROM casemanager_discord_settings
                WHERE guild_id = $1
                LIMIT 1
                `,
                [guildId]
            );

        let settings;

        if (result.rows.length) {

            settings =
                this.mapDatabaseSettings(
                    result.rows[0]
                );

        } else {

            settings =
                this.getEnvironmentDefaults();
        }

        this.cache.set(
            guildId,
            {
                settings,
                loadedAt: Date.now()
            }
        );

        return settings;
    }

    getEnvironmentDefaults() {

        return {

            mode:
                config.permissions.mode ===
                    "per-command"
                    ? "per-command"
                    : "global",

            globalRoleId:
                config.permissions.globalRoleId,

            perCommand: {

                case:
                    config.permissions.perCommand.case,

                evidence:
                    config.permissions.perCommand.evidence,

                timeline:
                    config.permissions.perCommand.timeline,

                investigators:
                    config.permissions.perCommand.investigators,

                status:
                    config.permissions.perCommand.status,

                settings:
                    config.permissions.perCommand.settings,

                permissions:
                    config.permissions.perCommand.permissions
            }
        };
    }

    mapDatabaseSettings(row) {

        return {

            mode:
                row.permission_mode ===
                    "per-command"
                    ? "per-command"
                    : "global",

            globalRoleId:
                row.global_role_id || "",

            perCommand: {

                case:
                    row.role_case || "",

                evidence:
                    row.role_evidence || "",

                timeline:
                    row.role_timeline || "",

                investigators:
                    row.role_investigators || "",

                status:
                    row.role_status || "",

                settings:
                    row.role_settings || "",

                permissions:
                    row.role_permissions || ""
            }
        };
    }

    async getRequiredRoleId(
        guildId,
        commandName
    ) {

        const settings =
            await this.getSettings(
                guildId
            );

        if (
            settings.mode ===
            "global"
        ) {

            return (
                settings.globalRoleId ||
                ""
            );
        }

        const commandRole =
            settings.perCommand[
                String(
                    commandName || ""
                ).toLowerCase()
            ];

        if (
            commandRole &&
            commandRole.trim() !== ""
        ) {

            return commandRole.trim();
        }

        return (
            settings.globalRoleId ||
            ""
        );
    }

    async hasPermission(
        member,
        commandName
    ) {

        if (!member) {
            return false;
        }

        if (!member.guild) {
            return false;
        }

        // Server owner always has access.
        if (
            member.guild.ownerId ===
            member.id
        ) {

            return true;
        }

        const requiredRoleId =
            await this.getRequiredRoleId(
                member.guild.id,
                commandName
            );

        // Fail closed if no role is configured.
        if (!requiredRoleId) {
            return false;
        }

        return member.roles.cache.has(
            requiredRoleId
        );
    }

    async saveSettings(
        guildId,
        settings,
        updatedBy
    ) {

        if (!guildId) {

            throw new Error(
                "Guild ID is required."
            );
        }

        const mode =
            settings.mode ===
                "per-command"
                ? "per-command"
                : "global";

        await database.query(
            `
            INSERT INTO casemanager_discord_settings (
                guild_id,
                permission_mode,
                global_role_id,
                role_case,
                role_evidence,
                role_timeline,
                role_investigators,
                role_status,
                role_settings,
                role_permissions,
                updated_by,
                updated_at
            )
            VALUES (
                $1,
                $2,
                $3,
                $4,
                $5,
                $6,
                $7,
                $8,
                $9,
                $10,
                $11,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (guild_id)
            DO UPDATE SET
                permission_mode =
                    EXCLUDED.permission_mode,

                global_role_id =
                    EXCLUDED.global_role_id,

                role_case =
                    EXCLUDED.role_case,

                role_evidence =
                    EXCLUDED.role_evidence,

                role_timeline =
                    EXCLUDED.role_timeline,

                role_investigators =
                    EXCLUDED.role_investigators,

                role_status =
                    EXCLUDED.role_status,

                role_settings =
                    EXCLUDED.role_settings,

                role_permissions =
                    EXCLUDED.role_permissions,

                updated_by =
                    EXCLUDED.updated_by,

                updated_at =
                    CURRENT_TIMESTAMP
            `,
            [
                guildId,

                mode,

                normalizeRoleId(
                    settings.globalRoleId
                ),

                normalizeRoleId(
                    settings.perCommand?.case
                ),

                normalizeRoleId(
                    settings.perCommand?.evidence
                ),

                normalizeRoleId(
                    settings.perCommand?.timeline
                ),

                normalizeRoleId(
                    settings.perCommand?.investigators
                ),

                normalizeRoleId(
                    settings.perCommand?.status
                ),

                normalizeRoleId(
                    settings.perCommand?.settings
                ),

                normalizeRoleId(
                    settings.perCommand?.permissions
                ),

                updatedBy || null
            ]
        );

        this.clearCache(
            guildId
        );
    }

    async getPermissionInfo(
        guildId,
        commandName
    ) {

        const settings =
            await this.getSettings(
                guildId
            );

        const roleId =
            await this.getRequiredRoleId(
                guildId,
                commandName
            );

        return {

            mode:
                settings.mode,

            roleId:
                roleId || null,

            configured:
                Boolean(roleId),

            description:
                this.getModeDescription(
                    settings
                )
        };
    }

    async validateGuild(
        guildId
    ) {

        const settings =
            await this.getSettings(
                guildId
            );

        const errors = [];

        if (
            settings.mode ===
            "global"
        ) {

            if (
                !settings.globalRoleId
            ) {

                errors.push(
                    "No global CaseManager role is configured."
                );
            }

        } else {

            const roles =
                Object.values(
                    settings.perCommand
                ).filter(
                    role =>
                        role &&
                        role.trim() !== ""
                );

            if (
                roles.length === 0 &&
                !settings.globalRoleId
            ) {

                errors.push(
                    "No CaseManager command roles are configured."
                );
            }
        }

        return {

            valid:
                errors.length === 0,

            errors
        };
    }

    getModeDescription(
        settings
    ) {

        if (
            settings.mode ===
            "global"
        ) {

            if (
                !settings.globalRoleId
            ) {

                return (
                    "Global mode — no role configured."
                );
            }

            return (
                "Global mode — one role controls CaseManager."
            );
        }

        return (
            "Per-command mode — individual commands can use different roles."
        );
    }

    clearCache(
        guildId
    ) {

        if (guildId) {

            this.cache.delete(
                guildId
            );

        } else {

            this.cache.clear();
        }
    }
}

function normalizeRoleId(
    roleId
) {

    if (
        !roleId ||
        typeof roleId !== "string"
    ) {

        return null;
    }

    const value =
        roleId.trim();

    return value || null;
}

module.exports =
    new PermissionService();