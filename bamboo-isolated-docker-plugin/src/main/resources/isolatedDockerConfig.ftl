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

<div id="errorMessage">
</div>

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
            <label for="architectureList">Architecture List</label>
            <textarea type="text" class="text text-field" id="architectureList"></textarea>
            <div class="description" id="desc-architectureList">
                Comma separated list of architectures available. The first one in the list will be the default in the selection dropdown.<br>
                It is recommended that if you do not want to force users to choose, add "default" as the first key, otherwise this may be omitted.<br>
                Architecture names will have leading and trailing whitespace trimmed.
                <br><br>
                Example:
                <code>default,amd64,arm64</code>
            </div>
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>

