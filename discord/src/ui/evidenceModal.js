const {
    ModalBuilder,
    LabelBuilder,
    FileUploadBuilder,
    TextInputBuilder,
    TextInputStyle
} = require("discord.js");


/*
 * ============================================================
 * CaseManager — Discord Evidence Upload Modal
 * ============================================================
 *
 * Modal flow:
 *
 * case:evidence:add:<caseId>
 *          ↓
 * evidence upload modal
 *          ↓
 * file + optional description
 *          ↓
 * interactionCreate.js
 *
 * Discord currently exposes uploaded modal files through
 * interaction.fields.getUploadedFiles().
 * ============================================================
 */


/*
 * Maximum number of files accepted in one submission.
 *
 * Discord's File Upload component supports up to 10 files.
 */
const MAX_FILES = 5;


/*
 * Build the evidence upload modal.
 *
 * @param {number|string} caseId
 * @returns {ModalBuilder}
 */
function evidenceUploadModal(caseId) {

    const normalizedCaseId =
        Number.parseInt(
            String(caseId || ""),
            10
        );


    if (
        !Number.isSafeInteger(normalizedCaseId) ||
        normalizedCaseId <= 0
    ) {

        throw new Error(
            "Invalid case ID."
        );
    }


    /*
     * --------------------------------------------------------
     * File upload
     * --------------------------------------------------------
     *
     * We intentionally don't restrict MIME types here.
     *
     * MediaEvidenceService performs the authoritative
     * validation against the CaseManager configuration.
     *
     * This also allows evidence such as:
     *
     * - screenshots
     * - videos
     * - exported chat logs
     * - documents
     * - other moderation evidence
     */

    const fileUpload =
        new FileUploadBuilder()
            .setCustomId(
                "evidence_files"
            )
            .setMinValues(1)
            .setMaxValues(
                MAX_FILES
            )
            .setRequired(true);


    const fileLabel =
        new LabelBuilder()
            .setLabel(
                "Evidence file(s)"
            )
            .setDescription(
                "Upload up to 5 files. CaseManager will validate their type and size."
            )
            .setFileUploadComponent(
                fileUpload
            );


    /*
     * --------------------------------------------------------
     * Description
     * --------------------------------------------------------
     */

    const descriptionInput =
        new TextInputBuilder()
            .setCustomId(
                "evidence_description"
            )
            .setStyle(
                TextInputStyle.Paragraph
            )
            .setPlaceholder(
                "Optional context about this evidence..."
            )
            .setRequired(false)
            .setMaxLength(2000);


    const descriptionLabel =
        new LabelBuilder()
            .setLabel(
                "Description"
            )
            .setDescription(
                "Optional note explaining what the evidence shows."
            )
            .setTextInputComponent(
                descriptionInput
            );


    /*
     * --------------------------------------------------------
     * Modal
     * --------------------------------------------------------
     */

    return new ModalBuilder()
        .setCustomId(
            `case:evidence:upload:${normalizedCaseId}`
        )
        .setTitle(
            `Add Evidence — Case #${normalizedCaseId}`
        )
        .addLabelComponents(
            fileLabel,
            descriptionLabel
        );
}


module.exports = {
    evidenceUploadModal,
    MAX_FILES
};