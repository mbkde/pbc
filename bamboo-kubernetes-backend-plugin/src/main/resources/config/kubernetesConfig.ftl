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

<form id="setBambooSidekick" class="aui">
    <fieldset>

        <div class="field-group">
            <label for="sidekickToUse">Bamboo Sidekick Image</label>
            <input type="text" class="text long-field" id="sidekickToUse"
                    placeholder=""></input>
        </div>
        <div class="field-group">
            <label for="serviceUrl">Service URL</label>
            <input type="text" class="text long-field" id="serviceUrl"></input>
            <div class="description" id="desc-serviceUrl">
                Kubernetes cluster URL
            </div>
        </div>
        <div class="field-group">
            <label for="namespaceToUse">Kubernetes Namespace</label>
            <input type="text" class="text long-field" id="namespaceToUse"></input>
            <div class="description" id="desc-namespaceToUse">
                Schedule agents in this Kubernetes namespace
            </div>
        </div>
        <div class="field-group">
            <label for="roleToUse">AWS IAM Role to use</label>
            <input type="text" class="text long-field" id="roleToUse"</input>
            <div class="description" id="desc-roleToUse">
                The bamboo agents will assume this AWS IAM role when running (assumes https://github.com/jtblin/kube2iam to be configured on the cluster)
            </div>
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>

