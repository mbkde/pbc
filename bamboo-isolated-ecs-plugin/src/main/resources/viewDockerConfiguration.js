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
        var clusterList = AJS.$("#clusterList");
        for (var i = 0; i < l; i++) {
            clusterList.append('<li><a href="javascript:setCluster(\'' + clusters[i] + '\')">' + clusters[i] + '</a></li>');
        }
        return clusters;
    }

    function processCurrentCluster(response) {
        AJS.$("#currentCluster").append(response.cluster);
    }

    function processCurrentSidekick(response) {
        AJS.$("#sidekickToUse").attr("placeholder", "Current: " + response.sidekick).val("").focus().blur();
    }
    
    function processCurrentASG(response) {
        AJS.$("#asgToUse").attr("placeholder", "Current: " + response.asg).val("").focus().blur();
    }

    function drawTable(data) {
        var table = AJS.$("#dockerImageTable tbody");
        AJS.$.each(data.mappings, function(i, mapping) {
            appendTableRow(table, mapping);
        });
    }
    
    function appendTableRow(parent, mapping) {
            parent.append('<tr id="row-revision-' + mapping.revision + '">' + 
                         "<td>" + mapping.dockerImage + "</td>" + 
                         '<td><button type="button" class="aui-button" onclick="deleteImage(' + mapping.revision + ')">Deregister</button></td>' +
                         '<td><a href="/admin/viewDockerUsages.action?revision=' + mapping.revision + '&image=' + mapping.dockerImage + '">Usages</a></td>' +
                         "</tr>");
    }

    function deleteImage(revision) {
        AJS.$.ajax({
            type: "DELETE",
            url: restEndpoint + revision,
            success: function () {
                AJS.$("#dockerImageTable #row-revision-" + revision).remove();
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }

    function registerImage() {
        var dockerImage = AJS.$("#dockerImageToRegister");
        AJS.$.ajax({
            type: "POST",
            url: restEndpoint,
            contentType: 'application/json',
            data: '{"dockerImage": "' + dockerImage.val().trim() + '" }',
            success: function (response) {
                var mapping = {};
                mapping.revision = response.revision;
                mapping.dockerImage = dockerImage.val().trim();
                var table = AJS.$("#dockerImageTable tbody");
                appendTableRow(table, mapping);
                dockerImage.val("");
                
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
                //no reload necessary
//                location.reload(true);
            },
            error: function (err) {
                alert(err.responseText);
                processResource(processCurrentCluster, "cluster");
            }
        });
    }

    function setSidekick() {
        var sidekick = AJS.$("#sidekickToUse").val();
        AJS.$.ajax({
            type: "POST",
            url: restEndpoint + "sidekick",
            contentType: 'application/json',
            data: '{"sidekick": "' + sidekick.trim() + '" }',
            success: function () {
                processResource(processCurrentSidekick, "sidekick");
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }

    function resetSidekick() {
        AJS.$.ajax({
            type: "POST",
            url: restEndpoint + "sidekick/reset",
            contentType: 'application/json',
            data: '{}',
            success: function () {
                processResource(processCurrentSidekick, "sidekick");
            },
            error: function (err) {
                alert(err.responseText);
            }
        });
    }
    
    function setASG() {
        var asg = AJS.$("#asgToUse").val();
        AJS.$.ajax({
            type: "POST",
            url: restEndpoint + "asg",
            contentType: 'application/json',
            data: '{"asg": "' + asg.trim() + '" }',
            success: function () {
                processResource(processCurrentASG, "asg");
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
    processResource(processCurrentSidekick, "sidekick");
    processResource(processCurrentASG, "asg");
});


