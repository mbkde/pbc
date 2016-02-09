/* 
 * Copyright 2016 Atlassian.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
    var restEndpoint = AJS.contextPath() + "/rest/docker/latest/";
    function processResource(callback, relativeEndpoint) {
        AJS.$.ajax({
                type: 'GET',
                url: restEndpoint + relativeEndpoint,
                success: function (text) {
                    callback(text);
                },
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    console.error(textStatus);
                }

            });
    }

    function processMappings(blob) {
        drawTable(blob);
    }

    function processValidClusters(blob) {
        var clusters = blob.clusters;
        var l = clusters.length;
        var clusterList = document.getElementById("clusterList");
        for (var i = 0; i < l; i++) {
            clusterList.innerHTML += '<li><a href="javascript:setCluster(\'' + clusters[i] + '\')">' + clusters[i] + '</a></li>';
        }
        return clusters;
    }

    function processCurrentCluster(cluster) {
        var currentCluster = document.getElementById("currentCluster");//TODO jquery
        currentCluster.innerHTML += cluster.currentCluster;
    }

    function drawTable(data) {
        var mappings = data.mappings;
        var l = mappings.length;
        var table = document.getElementById("dockerImageTable"); //TODO jquery
        for (var i = 0; i < l; i++) {
            var dockerImage = mappings[i]["dockerImage"];
            var revision = mappings[i]["revision"];
            var row = table.insertRow();
            var cell1 = row.insertCell(0);
            var cell2 = row.insertCell(1);
            cell1.innerHTML = dockerImage;
            cell2.innerHTML = '<button type="button" class="aui-button" onclick="deleteImage(' + revision + ')">Deregister</button>';
        }
    }

    function deleteImage(revision) {
        AJS.$.ajax({
            type: "DELETE",
            url: restEndpoint + revision,
            success: function () {
                location.reload(true);
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }

    function registerImage() {
        var dockerImage = document.getElementById("dockerImageToRegister").value; //TODO jquery
        AJS.$.ajax({
            type: "POST",
            url: restEndpoint,
            contentType: 'application/json',
            data: '{"dockerImage": "' + dockerImage.trim() + '" }',
            success: function () {
                location.reload(true); //TODO smarter reload
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }

    function setCluster(cluster) {
        AJS.$.ajax({
            type: "POST",
            url: restEndpoint + "cluster",
            contentType: 'application/json',
            data: '{"cluster": "' + cluster.trim() + '" }',
            success: function () {
                location.reload(true); //TODO smarter reload
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }
    
AJS.$(document).ready(function() {
    processResource(processMappings, "");
    processResource(processValidClusters, "cluster/valid");
    processResource(processCurrentCluster, "cluster");
});


