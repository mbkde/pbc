<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.admin">
    <title>Per-build Container ECS backend</title>
    ${webResourceManager.requireResourcesForContext("viewDockerConfiguration")}
</head>

<body>
    <h1>Per-build Container ECS Backend</h1>
<br>
Global Configuration for running Per-build Container agents on AWS ECS cluster. The Bamboo plugin
manages the scaling and scheduling of Bamboo agents on ECS.
</br>
<br>
        Only Docker images (and tags) registered with ECS can run in the cluster. Below is the list of images known to this Bamboo server/ECS cluster. 
List is updated on running a plan the first time, not editing it.

            <h2>Registered Docker Images</h2><br>

            <table id="dockerImageTable" class="aui">
                <tr>
                    <th>Docker Image</th>
                    <th></th>
                    <th></th>
                </tr>
            </table>

            <br>

<h2>ECS cluster configuration</h2>
    Configure the ECS cluster associated with this Bamboo server.

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
            <label for="currentCluster">ECS Cluster</label>
            <input type="text" class="text long-field" id="currentCluster"
                    placeholder="Autocomplete available clusters"></input>
            <div class="description" id="desc-currentCluster">
                Name of ECS cluster to run per-build container agents in
            </div>
        </div>
        <div class="field-group">
            <label for="asgToUse">AutoScaling Group</label>
            <input type="text" class="text long-field" id="asgToUse"
                    placeholder=""></input>
            <div class="description" id="desc-asgToUse">
                Name of Auto Scaling Group that is backing the ECS Cluster used by this Bamboo server.
            </div>
        </div>

        <div class="field-group">
            <label for="logDriver">Log Driver</label>
            <input type="text" class="text long-field" id="logDriver"
                    placeholder="Default if empty"></input>
            <div class="description" id="desc_logDriver">
                    Log Driver to use to log the Bamboo agent container output.
            </div>
        </div>

        <div class="field-group">
            <a id='docker_addLogOption' class='aui-link'>Add Log Driver Option</a>
            <label for="logOptionTable" id="fieldLabelArea_logOption">Log Driver Options</label>
            <table id="logOptionTable" class="aui">
                <tr>
                    <th>Key</th>
                    <th>Value</th>
                    <th></th>
                </t>
            </table>
            <div class="description" id="desc_logOptionTable">
                Log Configuration Options related to the selected Log Driver
            </div>
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setEcsConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>