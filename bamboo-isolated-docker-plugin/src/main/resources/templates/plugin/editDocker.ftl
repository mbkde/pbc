
${webResourceManager.requireResourcesForContext("docker.jobConfiguration")}

[@ui.bambooSection title=" " descriptionKey='isolated.docker.misc.header.description']
    [@ww.textfield cssClass='long-field docker-container-autocomplete' required=true 
        labelKey='isolated.docker.image' name='custom.isolated.docker.image' descriptionKey="isolated.docker.image.description"
        /]

    [#--  Only show the CPU architecture section if the server has a non-empty config or a job has the property already configured  --]
    [#if architectureList?size gt 0]
    [@ww.select labelKey='isolated.docker.architecture' name='custom.isolated.docker.architecture' descriptionKey="isolated.docker.architecture.description"
    list=architectureList listKey='first' listValue='second' cssClass="long-field" ]
    [/@ww.select]
    [/#if]

    [@ww.select labelKey='isolated.docker.size' name='custom.isolated.docker.imageSize'
        list=imageSizes listKey='first' listValue='second' cssClass="long-field" ]
    [/@ww.select]
    [@ww.hidden cssClass='long-field docker-extra-containers' name='custom.isolated.docker.extraContainers' /]
    [#include "extraContainersUI.ftl"]

    [@ww.textfield cssClass='long-field' required=false
        labelKey='isolated.docker.awsRole' name='custom.isolated.docker.awsRole' descriptionKey="isolated.docker.awsRole.description"
    /]

[/@ui.bambooSection]

[#include "extraContainersDialog.ftl"]

<script>
[#include "jobConfiguration.js"]
</script>
