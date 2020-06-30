(function ($) {
    $(document).ready(function () {
        const searchStr = new URLSearchParams(window.location.search);
        const deploymentId = searchStr.get("id");
        const hrefPath = AJS.contextPath() + "/rest/pbc-kubernetes/1.0/subjectIdForDeployment/" + deploymentId;

        // Prevents running on deployment pages not specific to a deployment
        if (deploymentId) {
            if (
                $("#project-configuration-actions").length ||
                $("#deployment-configuration-actions").length ||
                $("#environment-configuration-actions").length ||
                $("#deployment-version-actions").length ||
                $("#configure-result-actions").length
            ) {
                // "Configure deployment project" screen has "Create release" button in a separate section to the rest
                // of the items. Grab something lower down on the list so the new button appears at the bottom.
                // Both are still needed, as "Deploy" permission only grants the "Create release button" elsewhere in the UI
                const menuElement = $("#edit-deployment-project").get(0) || $("#create-deployment-version").get(0);
                var content = $(
                    '<li><a class="aui-icon-container" href="' +
                    hrefPath +
                    '">View AWS IAM Subject ID for PBC</a></li>'
                );

                content.appendTo(menuElement.parentNode.parentNode);
            } else {
                const innerHeaderElement = document.querySelector("#content > header > div");

                // Need absolute position if no buttons since the header won't expand to full size otherwise
                var content = $(
                    '<a id="subject-id-button" class="aui-buttons aui-button" ' +
                    'style="right: 10px; position: absolute; font-size: 14px; top: 30px; margin-left: 20px;" ' +
                    'href="' +
                    hrefPath +
                    '">View AWS IAM Subject ID for PBC</a>'
                );

                content.appendTo(innerHeaderElement);
            }
        }
    });
})(AJS.$);
