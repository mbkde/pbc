[@ww.textfield cssClass='long-field docker-container-autocomplete' labelKey='docker.task.key' name='dockerImage' descriptionKey='docker.task.description'/]

[@ww.select labelKey='isolated.docker.architecture' name='custom.isolated.docker.architecture' descriptionKey="isolated.docker.architecture.description"
list=architectureList listKey='first' listValue='second' cssClass="long-field" ]
[/@ww.select]

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