<head>
    <meta name="decorator" content="atl.admin">
</head>

<body>
<h1>Docker Images</h1>

<table id="dockerImageTable">
    <tr>
        <th>Docker Image</th>
        <th>Delete</th>
    </tr>
</table>

<h1>Register Images</h1>

<form id="registerDockerImage" onsubmit="registerImage()">
    Docker Image:<br>
    <input type="text" id="dockerImageToRegister"><br>
    <input type="submit" value="Submit">
</form>

<script type="text/javascript">
    var xmlHttp = new XMLHttpRequest();
    var restEndpoint = "https://staging-bamboo.internal.atlassian.com/rest/docker/latest/";
    xmlHttp.open("GET", restEndpoint, false);
    xmlHttp.send(null);
    var blob = JSON.parse(xmlHttp.responseText);
    drawTable(blob);

    function drawTable(data) {
        var l = data.length;
        for (var i = 0; i < l; i++) {
            drawRow(data[i]);
        }
    }

    function drawRow(row) {
        var blob = "<tr/>";
        blob += "<td>" + row["dockerImage"] +"</td>";
        blob += '<td> <button type="button" onclick="deleteImage(' + row["revision"] + ')"> </td>';
        document.getElementById("dockerImageTable").innerHTML += blob;
    }

    function deleteImage(revision) {
        $.ajax({
            type: "DELETE",
            url: restEndpoint+revision,
            success: function(msg) {
                if (msg === "OK") {
                    location.reload(true);
                } else {
                    alert(msg);
                }
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
            success: function(msg) {
                location.reload(true);
            },
            error: function(err, _, _) {
                alert(err.responseText);
            }
        });
    }
</script>

</body>