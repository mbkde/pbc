
${webResourceManager.requireResourcesForContext("docker.jobConfiguration")}

[@ui.bambooSection titleKey="isolated.docker.misc.header" descriptionKey='isolated.docker.misc.header.description']
    [@ww.textfield cssClass='long-field docker-container-autocomplete' required=true
        labelKey='isolated.docker.image' name='custom.isolated.docker.image' descriptionKey="isolated.docker.image.description" 
        /]
    [@ww.select labelKey='isolated.docker.size' name='custom.isolated.docker.imageSize'
        list=imageSizes listKey='first' listValue='second' cssClass="long-field" ]
    [/@ww.select]
    [@ww.hidden cssClass='long-field docker-extra-containers' name='custom.isolated.docker.extraContainers' /]
    [#include "extraContainersUI.ftl"]

    [@ww.textfield cssClass='long-field' required=false
    labelKey='isolated.docker.role' name='custom.isolated.docker.role' descriptionKey="isolated.docker.role.description"
    /]

    [@ww.textfield cssClass='long-field' required=false
    labelKey='isolated.docker.externalid' name='custom.isolated.docker.externalid' descriptionKey="isolated.docker.externalid.description" readonly=true
    /]
[/@ui.bambooSection]

[#include "extraContainersDialog.ftl"]

<script>
[#include "jobConfiguration.js"]
</script>
