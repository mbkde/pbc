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
        </div>
        <div class="field-group">
            <label for="asgToUse">AutoScaling Group</label>
            <input type="text" class="text long-field" id="asgToUse"
                    placeholder=""></input>
        </div>
        <button type="button" class="aui-button aui-button-primary" onclick="setEcsConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>