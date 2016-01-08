
[@ui.bambooSection titleKey="isolated.docker.misc.header" descriptionKey='isolated.docker.misc.header.description']
    [@ww.checkbox labelKey='isolated.docker.enabled' toggle='true' name='custom.isolated.docker.enabled'/]
    [@ui.bambooSection dependsOn='custom.isolated.docker.enabled' showOn=true]
        [@ww.textfield labelKey='isolated.docker.image' name='custom.isolated.docker.image' /]
    [/@ui.bambooSection]
[/@ui.bambooSection]