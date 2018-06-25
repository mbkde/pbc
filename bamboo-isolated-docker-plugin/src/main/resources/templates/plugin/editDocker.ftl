
${webResourceManager.requireResourcesForContext("docker.jobConfiguration")}

[@ui.bambooSection title=" " descriptionKey='isolated.docker.misc.header.description']
    [@ww.textfield cssClass='long-field docker-container-autocomplete' required=true 
        labelKey='isolated.docker.image' name='custom.isolated.docker.image' descriptionKey="isolated.docker.image.description" 
        /]
    [@ww.select labelKey='isolated.docker.size' name='custom.isolated.docker.imageSize'
        list=imageSizes listKey='first' listValue='second' cssClass="long-field" ]
    [/@ww.select]
    [@ww.hidden cssClass='long-field docker-extra-containers' name='custom.isolated.docker.extraContainers' /]
    [#include "extraContainersUI.ftl"]
[/@ui.bambooSection]

[#include "extraContainersDialog.ftl"]

<script>
[#include "jobConfiguration.js"]
</script>