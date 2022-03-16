<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.admin">
    <title>Per-build Container</title>
    ${webResourceManager.requireResourcesForContext("viewIsolatedDockerConfiguration")}
</head>

<body>
    <h1>Per-build Container</h1>
<br/>
Global Configuration for generic PBC settings
</br>


<h2>Generic PBC configuration</h2>

<form id="setRemoteConfig" class="aui">
    <fieldset>

        <div class="field-group">
            <label for="defaultImage">Default image:</label>
            <input type="text" class="text long-field" id="defaultImage"
                   placeholder=""/>
            <div class="description" id="desc-defaultImage">
                Default agent container image used
            </div>
        </div>

        <div class="field-group">
            <label for="maxAgentCreationPerMinute">PBC Agent Creation Throttling</label>
            <textarea type="text" class="text text-field" id="maxAgentCreationPerMinute"></textarea>
            <div class="description" id="desc-maxAgentCreationPerMinute">
                Specify the maximum number of PBC agents you want to be able to start up per minute.
            </div>
        </div>

        <div class="field-group">
            <label for="architectureConfig">Architecture Config</label>
            <textarea type="text" style="height: 200px" class="textarea long-field" id="architectureConfig"></textarea>
            <div class="description" id="desc-architectureConfig">
                YAML document of architectures available, with the key being the primary name and the value being the display name.<br>
                The first entry will be the default in the selection dropdown, with the items being shown in the same order as the YAML.<br>
                Architecture names will have leading and trailing whitespace trimmed.
                <br><br>
                Example:
                <pre><code>
amd64: "amd64 (x86_64)"
arm64: "arm64 (ARMv8 aarch64)"
                </code></pre>
            </div>
        </div>

        <div id="errorMessage" style="white-space: pre-line">
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>

