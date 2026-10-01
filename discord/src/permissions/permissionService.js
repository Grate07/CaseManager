const config = require("../config");

class PermissionService {

    constructor() {

        this.mode =
            config.permissions.mode === "per-command"
                ? "per-command"
                : "global";

        this.globalRoleId =
            config.permissions.globalRoleId;

        this.perCommand =
            config.permissions.perCommand;
    }


    /**
     * Returns the configured role ID for a command.
     */
    getRequiredRoleId(commandName) {

        if (this.mode === "global") {

            return this.globalRoleId;
        }

        const commandRole =
            this.perCommand[
                commandName.toLowerCase()
            ];

        /*
         * If a command does not have a specific
         * role configured, fall back to the
         * global role.
         */
        if (commandRole &&
            commandRole.trim() !== "") {

            return commandRole.trim();
        }

        return this.globalRoleId;
    }


    /**
     * Check whether a Discord member has
     * the required CaseManager role.
     */
    hasPermission(member, commandName) {

        if (!member) {
            return false;
        }

        const requiredRoleId =
            this.getRequiredRoleId(commandName);

        /*
         * No role configured.
         *
         * We deliberately deny access rather
         * than accidentally exposing commands.
         */
        if (!requiredRoleId) {
            return false;
        }

        return member.roles.cache.has(
            requiredRoleId
        );
    }


    /**
     * Return permission information useful
     * for the Components V2 UI.
     */
    getPermissionInfo(commandName) {

        const roleId =
            this.getRequiredRoleId(commandName);

        return {

            mode: this.mode,

            roleId: roleId || null,

            configured:
                Boolean(roleId)
        };
    }


    /**
     * Validate that the current configuration
     * has at least one usable role.
     */
    validateConfiguration() {

        const errors = [];

        if (this.mode === "global") {

            if (!this.globalRoleId) {

                errors.push(
                    "CASEMANAGER_ROLE_ID is not configured."
                );
            }

        } else {

            const configuredRoles =
                Object.values(
                    this.perCommand
                ).filter(
                    role =>
                        role &&
                        role.trim() !== ""
                );

            if (
                configuredRoles.length === 0 &&
                !this.globalRoleId
            ) {

                errors.push(
                    "No CaseManager Discord roles are configured."
                );
            }
        }

        return {
            valid: errors.length === 0,
            errors
        };
    }


    /**
     * Return a human-readable description
     * of the current permission mode.
     */
    getModeDescription() {

        if (this.mode === "global") {

            if (!this.globalRoleId) {

                return "Global role mode — no role configured.";
            }

            return (
                "Global role mode — all CaseManager commands " +
                "require the configured role."
            );
        }

        return (
            "Per-command role mode — commands may use " +
            "different Discord roles."
        );
    }
}


module.exports =
    new PermissionService();