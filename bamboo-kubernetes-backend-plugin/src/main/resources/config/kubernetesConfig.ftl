<head xmlns="http://www.w3.org/1999/html">
    [@ui.header pageKey="kubernetes.backend.config.heading" title=true /]
    <meta name="decorator" content="adminpage"/>
    ${webResourceManager.requireResourcesForContext("viewKubernetesConfiguration")}
</head>

<body>
    <h1>[@s.text name='kubernetes.backend.config.heading' /]</h1>
<p>
[@s.text name='kubernetes.backend.config.heading.description' /]
</p>

[@s.form submitLabelKey='global.buttons.update' cancelUri="${currentUrl}" id="setRemoteConfig" class="aui"]
    [@ui.bambooSection titleKey='kubernetes.backend.config.section.heading' ]
        [@s.textfield labelKey='kubernetes.backend.config.form.sidekickImage' name='sidekickToUse' cssClass='long-field' /]
        [@s.textfield labelKey='kubernetes.backend.config.form.currentContext' name='currentContext' placeholderKey='kubernetes.backend.config.form.currentContext.placeholder' cssClass='long-field' /]
        [@s.checkbox labelKey='kubernetes.backend.config.form.useClusterRegistry' toggle=true name='useClusterRegistry' /]
        [@ui.bambooSection dependsOn='useClusterRegistry' showOn=true ]
            [@s.textfield labelKey='kubernetes.backend.config.form.clusterRegistryAvailableSelector' name='clusterRegistryAvailableSelector' cssClass='long-field' /]
            [@s.textfield labelKey='kubernetes.backend.config.form.clusterRegistryPrimarySelector' name='clusterRegistryPrimarySelector' placeholderKey='kubernetes.backend.config.form.clusterRegistryPrimarySelector.placeholder' cssClass='long-field' /]
        [/@ui.bambooSection]
        [@s.textarea labelKey='kubernetes.backend.config.form.podTemplate' name='podTemplate' rows='20' cssClass='long-field' /]
        [@s.textarea labelKey='kubernetes.backend.config.form.architecturePodConfig' name='architecturePodConfig' rows='20' cssClass='long-field' /]
        <div class="description" id="desc-architecturePodConfig">
            Add your config for architecture-dependent sections of the pod template in YAML. Each top-level key should
            be a name of the architecture, with a sub-key "config" with its value being the YAML to be merged into the full pod spec.<br>

            If you require no extra config, use an empty map {}. The architectures specified here <strong>must</strong> exactly match those
            specified in the PBC General settings.<br>

            You must specify a "default" key at the top level which specifies which architecture is default in the case
            no architecture is specified. This ensures backwards compatibility; newly created plans will require an architecture.<br><br>

            Example:
            <pre><code>
default: amd64
amd64:
  config: {}
arm64:
  config:
    spec:
      nodeSelector:
        nodeGroup: myNode
                </code></pre>

        </div>
        [@ww.hidden name='showAwsSpecificFields' /]
        [#if action.showAwsSpecificFields]
            [@s.textarea labelKey='kubernetes.backend.config.form.iamRequestTemplate' name='iamRequestTemplate' rows='7' cssClass='long-field' /]
            [@s.textarea labelKey='kubernetes.backend.config.form.iamSubjectIdPrefix' name='iamSubjectIdPrefix' rows='7' cssClass='long-field' /]
        [/#if]
        [@s.textarea labelKey='kubernetes.backend.config.form.containerSizes' name='containerSizes' rows='20' cssClass='long-field' /]
        [@s.textfield labelKey='kubernetes.backend.config.form.podLogsUrl' name='podLogsUrl' cssClass='long-field' /]
    [/@ui.bambooSection]
    <fieldset>
        <div id="errorMessage" style="white-space: pre-line">
        </div>

        <div class="save-status"></div>
        <input type="hidden" id="load_complete" value="false"/>
    </fieldset>
[/@s.form]
    <script lang="text/javascript">
        require(['feature/kubernetes-backend-plugin/config'], function(Config) {
            Config.onInit();
        })
    </script>
</body>
