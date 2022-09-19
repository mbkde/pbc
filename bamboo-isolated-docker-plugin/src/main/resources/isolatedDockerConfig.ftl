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
            <label for="enableSwitch">Enable</label>
            <aui-toggle id="enableSwitch" label="enableSwitch"></aui-toggle>
        </div>

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

        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>

