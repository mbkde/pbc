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

<form id="setRemoteConfig" class="aui">
    <fieldset>

        <div class="field-group">
            <label for="sidekickToUse">Bamboo Sidekick Image</label>
            <input type="text" class="text long-field" id="sidekickToUse"
                    placeholder=""></input>
        </div>
        <div class="field-group">
            <label for="curentContext">Current Context</label>
            <input type="text" class="text long-field" id="currentContext"
                   placeholder="Default context"></input>
            <div class="description" id="desc-currentContext">
                Explicitly set kubernetes context to use by the plugin. Empty value is to rely on default context.
            </div>
        </div>
        <div class="field-group">
            <div id="fieldArea_useClusterRegistry" class="checkbox">
                <input type="checkbox" name="useClusterRegistry" id="useClusterRegistry" 
                    onclick="updateClusterRegistry()" class="checkbox">
                <label for="useClusterRegistry" id="label_useClusterRegistry">Use Cluster Registry</label>
                <div class="description" id="desc-useClusterRegistry">
                    When using Cluster Registry, we query the current context (explicitly defined or implicit default based on bamboo server config)
                     for cluster(s) available to run Bamboo agents on.
                </div>
            </div>
            <div class="field-group dependsClusterRegistryShow" style="display:none;">
                <label for="clusterRegistryAvailableSelector">Available cluster label</label>
                <input type="text" class="text long-field" id="clusterRegistryAvailableSelector"
                       placeholder=""></input>
                <div class="description" id="desc-clusterRegistryAvailableSelector">
                    Label name on cluster(s) in registry. The expected label value is the name of context defined on Bamboo server.
                    The value will be used to associate context (cluster url, namespace and credentials) to access the given cluster and
                    access the cluster from Bamboo server.
                </div>
            </div>

            <div class="field-group dependsClusterRegistryShow" style="display:none;">
                <label for="clusterRegistryPrimarySelector">Primary cluster label</label>
                <input type="text" class="text long-field" id="clusterRegistryPrimarySelector"
                       placeholder="If not defined randomly select one of available clusters"></input>
                <div class="description" id="desc-clusterRegistryPrimarySelector">
                    Label name on cluster(s) in registry. The label value doesn't matter.
                    If present, only marked clusters will be used to schedule new pods on. If multiple are marked random one is picked.
                    If not present on any cluster, one of available clusters is used.
                </div>
            </div>

        </div>


        <div class="field-group">
            <label for="podTemplate">Pod Template</label>
            <textarea type="text" style="height: 200px" class="textarea long-field" id="podTemplate"></textarea>
            <div class="description" id="desc-podTemplate">
                Add your pod configuration here
            </div>
        </div>

        <div class="field-group">
            <label for="architecturePodConfig">Architecture Dependent Pod Config</label>
            <textarea type="text" style="height: 200px" class="textarea long-field" id="architecturePodConfig"></textarea>
            <div class="description" id="desc-architecturePodConfig">
                Add your config for architecture-dependent sections of the pod template in YAML. Each top-level key should
                be name of the architecture, with a sub-key "config" with its value being the YAML to be merged into the full pod spec.<br>

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
        </div>

        <div class="field-group">
            <label for="artifactoryCacheAllowList">Artifactory Cache Allowed Builds</label>
            <textarea type="text" style="height: 200px" class="textarea long-field" id="artifactoryCacheAllowList"></textarea>
            <div class="description" id="desc-artifactoryCacheAllowList">
                Allow list for builds which will use a mounted artifactory cache volume, one build key per line as a YAML list.
                <br><br>
                Example:
                <pre><code>
- SYNTH-PBCSYNTH
- SYNTH-IDPLUGIN
                </code></pre>
            </div>
        </div>

        <div class="field-group">
            <label for="artifactoryCachePodSpec">Artifactory Cache Pod Spec</label>
            <textarea type="text" style="height: 200px" class="textarea long-field" id="artifactoryCachePodSpec"></textarea>
            <div class="description" id="desc-artifactoryCachePodSpec">
                Pod Spec to add to builds that will use the cache
            </div>
        </div>

        <div class="field-group">
            <label for="iamRequestTemplate">IAM Request Template</label>
            <textarea type="text" style="height: 200px" class="textarea long-field" id="iamRequestTemplate"></textarea>
            <div class="description" id="desc-iamRequestTemplate">
                Add your IAM request configuration here
            </div>
        </div>

        <div class="field-group">
            <label for="iamSubjectIdPrefix">IAM Subject ID Prefix</label>
            <textarea type="text" style="height: 200px" class="text text-field" id="iamSubjectIdPrefix"></textarea>
            <div class="description" id="desc-iamSubjectIdPrefix">
                Add your IAM Subject ID prefix here. This prefix is only used when displaying the Subject ID from the
                "View AWS IAM Subject ID for PBC" dropdown and not used internally.
            </div>
        </div>

        <div class="field-group">
            <label for="containerSizes">Container size definitions</label>
            <textarea type="text" style="height: 200px" class="textarea long-field" id="containerSizes"></textarea>
            <div class="description" id="desc-containerSizes">
                Define container memory/cpu size limits for main and extra containers.
            </div>
        </div>

        <div class="field-group">
            <label for="podLogsUrl">Container Logs URL</label>
            <input type="text" class="text long-field" id="podLogsUrl"
                    placeholder=""></input>
            <div class="description" id="desc-podLogsUrl">
                URL template to reach container logs for given pod and container. POD_NAME and CONTAINER_NAME constants 
                in the URL will be replaced with actual values.
            </div>
        </div>

        <div id="errorMessage" style="white-space: pre-line">
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>

