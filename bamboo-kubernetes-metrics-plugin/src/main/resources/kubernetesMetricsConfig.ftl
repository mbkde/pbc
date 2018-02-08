<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.admin">
    <title>Per-build Container Kubernetes Backend</title>
    ${webResourceManager.requireResourcesForContext("viewKubernetesMetricsConfiguration")}
</head>

<body>
    <h1>Per-build Container Kubernetes Metrics</h1>
<br>
Global Configuration for collecting Docker container metrics for PBC agents running in Kubernetes.
</br>


<h2>Kubernetes metrics configuration</h2>

<div id="errorMessage">
</div>

<form id="setRemoteConfig" class="aui">
    <fieldset>

        <div class="field-group">
            <label for="prometheusUrl">Prometheus URL:</label>
            <input type="text" class="text long-field" id="prometheusUrl"
                    placeholder=""></input>
            <div class="description" id="desc-prometheusUrl">
                URL to Prometheus instance from inside the running agent container.
            </div>
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>

