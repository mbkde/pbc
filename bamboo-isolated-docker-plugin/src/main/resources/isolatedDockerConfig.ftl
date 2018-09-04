<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.admin">
    <title>Per-build Container</title>
    ${webResourceManager.requireResourcesForContext("viewIsolatedDockerConfiguration")}
</head>

<body>
    <h1>Per-build Container</h1>
<br>
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
                    placeholder=""></input>
            <div class="description" id="desc-defaultImage">
                Default agent container image used
            </div>
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>

