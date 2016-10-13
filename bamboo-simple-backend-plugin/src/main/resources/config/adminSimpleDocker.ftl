<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.admin">
    <title>Per-build container Docker Backend</title>
    ${webResourceManager.requireResourcesForContext("simpleDockerAdminPBC")}
</head>

<body>
    <h1>Per-build container Docker Backend</h1><br>
<div id="errorMessage">
</div>

    A simple backend scheduling jobs via docker-compose. Assumes docker cli + docker-compose
is installed on the Bamboo Server to be able to run the jobs locally, in remote docker-machine

<form id="configureDocker" class="aui">
    <fieldset>

        <div class="field-group">
            <label for="docker-certs">Path to certs</label>
            <input type="text" class="text long-field" id="docker-certs"
                    placeholder="Default location"></input>
        </div>
        <div class="field-group">
            <label for="docker-url">Docker host</label>
            <input type="text" class="text long-field" id="docker-url"
                    placeholder="Local docker"></input>
        </div>
        <div class="field-group">
            <label for="docker-api">Docker API version</label>
            <input type="text" class="text long-field" id="docker-api"
                    placeholder=""></input>
        </div>


        <div class="field-group">
            <label for="sidekick">Sidekick</label> 
            <input type="text" class="text long-field" id="sidekick"
                    placeholder=""></input>
        </div>
        <div class="field-group">
            <div class="checkbox">
                <input type="checkbox" id="sidekick-sel" value="true"/>
                <label for="sidekick-sel">Sidekick is image (otherwise local folder assumed)</label> 
            </div>
        </div>

        <button type="button" class="aui-button aui-button-primary" onclick="setSimpleDocker()">Save</button>
        <div class="save-status"/>
    </fieldset>
</form>
</body>
