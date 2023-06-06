<head xmlns="http://www.w3.org/1999/html">
    [@ui.header pageKey="isolated.docker.config.heading" title=true /]
    <meta name="decorator" content="adminpage"/>
    ${webResourceManager.requireResourcesForContext("viewIsolatedDockerConfiguration")}
</head>

<body>
<h1>[@s.text name='isolated.docker.config.heading' /]</h1>

[@s.form id="setRemoteConfig" submitLabelKey='global.buttons.update' cancelUri="${currentUrl}" ]
    [@ui.bambooSection titleKey='isolated.docker.config.section.heading' ]
        <div class="field-group">
            <label for="enableSwitch">Enable</label>
            <aui-toggle id="enableSwitch" label="enableSwitch"></aui-toggle>
        </div>
        [@s.checkbox labelKey='isolated.docker.config.form.awsVendor' name='awsVendor' value='aws'/]
        [@s.textfield labelKey='isolated.docker.config.form.defaultImage' name='defaultImage' cssClass='long-field' /]
        [@s.textfield labelKey='isolated.docker.config.form.throttling' name='maxAgentCreationPerMinute' /]
        [@s.textarea labelKey='isolated.docker.config.form.architecture' name='architectureConfig' rows='11' cssClass="long-field" /]
        <div class="description" id="desc-architectureConfig">
            YAML document of architectures available, with the key being the primary name and the value being the
            display name.<br>
            The first entry will be the default in the selection dropdown, with the items being shown in the same order
            as the YAML.<br>
            Architecture names will have leading and trailing whitespace trimmed.
            <br><br>
            Example:
            <pre><code>
amd64: "amd64 (x86_64)"
arm64: "arm64 (ARMv8 aarch64)"
                </code></pre>
        </div>
        [@s.textfield labelKey='isolated.docker.config.form.agentCleanupTime' name='agentCleanupTime' /]
        [@s.textfield labelKey='isolated.docker.config.form.agentRemovalTime' name='agentRemovalTime' /]
    [/@ui.bambooSection]
    <div id="errorMessage" style="white-space: pre-line">
    </div>
    <div class="save-status"></div>
    <input type="hidden" id="load_complete" value="false"/>
[/@s.form]
<script lang="text/javascript">
    require(['feature/isolate-docker-plugin/config'], function (Config) {
        Config.onInit();
    })
</script>
</body>

