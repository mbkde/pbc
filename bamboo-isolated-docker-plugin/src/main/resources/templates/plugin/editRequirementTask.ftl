[@ww.textfield cssClass='long-field docker-container-autocomplete' labelKey='docker.task.key' name='dockerImage' descriptionKey='docker.task.description'/]

[#--  Only show the CPU architecture section if the server has a non-empty config or a job has the property already configured  --]
[#if architectureConfig?size gt 0]
[@ww.select labelKey='isolated.docker.architecture' name='custom.isolated.docker.architecture' descriptionKey="isolated.docker.architecture.description"
list=architectureConfig listKey='first' listValue='second' cssClass="long-field" ]
[/@ww.select]
[/#if]

[@ww.select labelKey='isolated.docker.size' name='dockerImageSize'
    list=imageSizes listKey='first' listValue='second' cssClass="long-field"]
[/@ww.select]
[@ww.hidden cssClass='long-field docker-extra-containers' name='extraContainers'/]
[#include "extraContainersUI.ftl"]
[#include "extraContainersDialog.ftl"]

<script>
[#include "jobConfiguration.js"]
</script>

<script>
AJS.$(document).ready(function () {
    var dialogs = AJS.$('section#dockerExtraImage-dialog');
    if (dialogs.length == 2) {
        dialogs.first().remove();
    }
});
</script>