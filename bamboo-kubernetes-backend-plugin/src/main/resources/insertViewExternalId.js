(function($) {
    $(document).ready(function() {
        if($('#project-configuration-actions').length) {
            var searchStr = window.location.search;
            var deploymentId = searchStr.split("=")[1];

            var element = $('#create-deployment-version').get(0);
            var content = $(
                '<li><a class="aui-icon-container" href="' + AJS.contextPath()
                + '/rest/pbc-kubernetes/1.0/externalIdForDeployment/' + deploymentId
                + '">View External ID for PBC</a></li>'
            );

            content.appendTo(element.parentNode.parentNode);
        }
    });
})(AJS.$);
