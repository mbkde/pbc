${webResourceManager.requireResourcesForContext("docker.jobConfiguration")}

[@ui.bambooSection titleKey="isolated.docker.misc.header" descriptionKey='isolated.docker.misc.header.description']
    [@ww.checkbox labelKey='isolated.docker.enabled' toggle='true' name='custom.isolated.docker.enabled'/]
    [@ui.bambooSection dependsOn='custom.isolated.docker.enabled' showOn=true]
        [@ww.textfield cssClass='long-field' required=true labelKey='isolated.docker.image' name='custom.isolated.docker.image' descriptionKey="isolated.docker.image.description" /]
        [@ww.select labelKey='isolated.docker.size' name='custom.isolated.docker.imageSize'
            list=imageSizes listKey='first' listValue='second' cssClass="long-field"]
        [/@ww.select]
        [@ww.textarea cssClass='long-field' labelKey='isolated.docker.extra' name='custom.isolated.docker.extraContainers' descriptionKey="isolated.docker.extra.description" /]
    [/@ui.bambooSection]
[/@ui.bambooSection]