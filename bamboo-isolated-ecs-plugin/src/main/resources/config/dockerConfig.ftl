<head>
    <meta name="decorator" content="atl.admin">
    <title>Configure Isolated Docker</title>
</head>

<body>
<h1>Configure Isolated Docker</h1><br>

<h2>Registered Docker Images</h2><br>

<table id="dockerImageTable" class="aui">
    <tr>
        <th>Docker Image</th>
        <th></th>
    </tr>
</table>

<br>

<h2>Register Docker Images</h2>

<form id="registerDockerImage" class="aui">
    <fieldset>
        <legend><span>Register New Image</span></legend>
        <div class="field-group">
            <label for="textarea-id">Docker Repository</label>
            <textarea class="textarea" id="dockerImageToRegister"
                      placeholder="e.g. docker.atlassian.io/bamboo-arch-base-agent:latest"></textarea><br>
            <button type="button" class="aui-button" onclick="registerImage()">Register</button>
            <br>
        </div>
    </fieldset>
</form>

<br>

<h2>Set ECS Cluster</h2><br>

<!-- Trigger -->
<a href="#clusters" aria-owns="clusters" aria-haspopup="true" id="currentCluster"
   class="aui-button aui-style-default aui-dropdown2-trigger"></a>

<!-- Dropdown -->
<div id="clusters" class="aui-style-default aui-dropdown2">
    <ul class="aui-list-truncate" id="clusterList">
    </ul>
</div>

<script type="text/javascript">
    var restEndpoint = "https://staging-bamboo.internal.atlassian.com/rest/docker/latest/";
    var clusterList = document.getElementById("clusterList");
    var currentCluster = document.getElementById("currentCluster");

    processResource(processMappings, "");
    processResource(processValidClusters, "cluster/valid");
    processResource(processCurrentCluster, "cluster");

    function processResource(callback, relativeEndpoint) {
        var xmlHttp = new XMLHttpRequest();
        xmlHttp.open("GET", restEndpoint + relativeEndpoint, true);
        xmlHttp.onload = function () {
            if (xmlHttp.readyState === 4) {
                if (xmlHttp.status === 200) {
                    callback(xmlHttp.responseText);
                } else {
                    console.error(xmlHttp.statusText);
                }
            }
        };
        xmlHttp.onerror = function () {
            console.error(xmlHttp.statusText);
        };
        xmlHttp.send(null);
    }

    function processMappings(blob) {
        var mappings = JSON.parse(blob);
        drawTable(mappings);
    }

    function processValidClusters(blob) {
        var clusters = JSON.parse(blob);
        var l = clusters.length;
        for (var i = 0; i < l; i++) {
            clusterList.innerHTML += '<li><a href="javascript:setCluster(\'' + clusters[i] + '\')">' + clusters[i] + '</a></li>';
        }
        return clusters;
    }

    function processCurrentCluster(cluster) {

        currentCluster.innerHTML += cluster;
    }

    function drawTable(data) {
        var l = data.length;
        var table = document.getElementById("dockerImageTable");
        for (var i = 0; i < l; i++) {
            var row = table.insertRow();
            var cell1 = row.insertCell(0);
            var cell2 = row.insertCell(1);
            cell1.innerHTML = data[i]["dockerImage"];
            cell2.innerHTML = '<button type="button" class="aui-button" onclick="deleteImage(' + data[i]["revision"] + ')">Deregister</button>';
        }
    }

    function deleteImage(revision) {
        $.ajax({
            type: "DELETE",
            url: restEndpoint + revision,
            success: function (msg) {
                if (msg === "OK") {
                    location.reload(true);
                } else {
                    alert(msg);
                }
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }

    function registerImage() {
        var dockerImage = document.getElementById("dockerImageToRegister").value;
        $.ajax({
            type: "POST",
            url: restEndpoint,
            contentType: 'text/plain',
            data: dockerImage,
            success: function (msg) {
                location.reload(true);
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }

    function setCluster(clusterName) {
        $.ajax({
            type: "POST",
            url: restEndpoint + "cluster",
            contentType: 'text/plain',
            data: clusterName,
            success: function (msg) {
                location.reload(true);
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }

</script>

</body>