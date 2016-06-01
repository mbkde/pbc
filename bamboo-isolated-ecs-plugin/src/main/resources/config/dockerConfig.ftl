<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.admin">
    <title>Configure Isolated Docker</title>
    ${webResourceManager.requireResourcesForContext("viewDockerConfiguration")}
</head>

<body>
    <h1>ECS Isolated Docker Agents</h1><br>
        Only Docker images (and tags) registered with ECS can run in the cluster. Below is the list of images known to this Bamboo server/ECS cluster.

            <h2>Register new Docker Images</h2>

            <form id="registerDockerImage" class="aui">
                <fieldset>
                    <div class="field-group">
                        <label for="dockerImageToRegister">Register New Image</label>
                        <input type="text" class="text long-field" id="dockerImageToRegister"
                                  placeholder="e.g. docker.atlassian.io/buildeng/agent-baseagent"></input>
                        <button type="button" class="aui-button" onclick="registerImage()">Register</button>
                    </div>
                </fieldset>
            </form>

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


<form id="setBambooSidekick" class="aui">
    <fieldset>

        <div class="field-group">
            <label for="sidekickToUse">Bamboo Sidekick Image</label>
            <input type="text" class="text long-field" id="sidekickToUse"
                    placeholder=""></input>
            <button type="button" class="aui-button" onclick="setSidekick()">Set</button>
            <button type="button" class="aui-button" onclick="resetSidekick()">Reset</button>
        </div>
        <div class="field-group">
            <label for="currentCluster">ECS Cluster</label>
            <a href="#clusters" aria-owns="clusters" aria-haspopup="true" id="currentCluster"
               class="aui-button aui-style-default aui-dropdown2-trigger"></a>

            <!-- Dropdown -->
            <div id="clusters" class="aui-style-default aui-dropdown2">
                <ul class="aui-list-truncate" id="clusterList">
                </ul>
            </div>
        </div>
        <div class="field-group">
            <label for="asgToUse">AutoScaling Group</label>
            <input type="text" class="text long-field" id="asgToUse"
                    placeholder=""></input>
            <button type="button" class="aui-button" onclick="setASG()">Set</button>
        </div>
    </fieldset>
</form>

</body>