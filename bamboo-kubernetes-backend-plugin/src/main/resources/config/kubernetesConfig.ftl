<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.admin">
    <title>Per-build Container Kubernetes Backend</title>
    ${webResourceManager.requireResourcesForContext("viewKubernetesConfiguration")}
</head>

<body>
    <h1>Per-build Container Kubernetes Backend</h1>
<br>
Global Configuration for running Per-build Container agents using Kubernetes to schedule agents.
</br>


<h2>Kubernetes configuration</h2>

<div id="errorMessage">
</div>

<form id="setRemoteConfig" class="aui">
    <fieldset>

        <div class="field-group">
            <label for="sidekickToUse">Bamboo Sidekick Image</label>
            <input type="text" class="text long-field" id="sidekickToUse"
                    placeholder=""></input>
        </div>
        <div class="field-group">
            <label for="podTemplate">Pod Template</label>
            <textarea type="text" style="height: 200px" class="textarea long-field" id="podTemplate"></textarea>
            <div class="description" id="desc-podTemplate">
                Add your pod configuration here
            </div>
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>

