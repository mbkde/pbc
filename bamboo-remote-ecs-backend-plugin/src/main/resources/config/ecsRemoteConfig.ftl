<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.admin">
    <title>Per-build Container Remote ECS backend</title>
    ${webResourceManager.requireResourcesForContext("viewRemoteEcsConfiguration")}
</head>

<body>
    <h1>Per-build Container Remote ECS Backend</h1>
<br>
Global Configuration for running Per-build Container agents using remote service on AWS ECS cluster. The remote service
manages the scaling and scheduling of Bamboo agents on ECS.
</br>


<h2>ECS service configuration</h2>
    Configure the ECS service associated with this Bamboo server.

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
                Service URL that performs the scheduling and scaling of the ECS cluster
            </div>
        </div>
        <div class="field-group">
            <label for="roleToUse">AWS IAM Role to use</label>
            <input type="text" class="text long-field" id="roleToUse"</input>
            <div class="description" id="desc-roleToUse">
                The bamboo agents will assume this AWS IAM role when running
            </div>
        </div>

        <div class="field-group">
            <label for="preemptive">Preemptive scaling</label>
            <input type="checkbox" class="long-field" id="preemptive"></input>
            <div class="description" id="desc-preemptive">
               If checked, build's subsequent stages will reserve future capacity in the cluster
               during previous stage to allow cluster to scale ahead of time if necessary
            </div>
        </div>


        <button type="button" class="aui-button aui-button-primary" onclick="setRemoteConfig()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>

</body>
