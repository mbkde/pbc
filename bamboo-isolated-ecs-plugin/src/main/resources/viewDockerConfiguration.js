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
                    showError(textStatus + " " + errorThrown);
                }
            });
    }

    function processMappings(blob) {
        drawTable(blob);
    }

    function processValidClusters(blob) {
        var clusters = blob.clusters;
        AJS.$(".docker-container-autocomplete").autocomplete({
            minLength: 0,
//                    position: { my : "right top", at: "right bottom" },
            source: clusters
        }
        );
    }

    function processConfig(response) {
        AJS.$("#currentCluster").val(response.ecsClusterName);
        AJS.$("#sidekickToUse").val(response.sidekickImage);
        AJS.$("#asgToUse").val(response.autoScalingGroupName);
        updateStatus("");
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

    function setEcsConfig() {
        var config = {};
        config.sidekickImage = AJS.$("#sidekickToUse").val().trim();
        config.autoScalingGroupName = AJS.$("#asgToUse").val().trim();
        config.ecsClusterName = AJS.$("#currentCluster").val().trim();
        updateStatus("Saving...");

        AJS.$.ajax({
            type: "POST",
            url: restEndpoint + "config",
            contentType: 'application/json',
            data: JSON.stringify(config),
            success: function () {
                updateStatus("Saved");
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                updateStatus("");
                showError(textStatus + " " + errorThrown);
            }
        });
    }

    function updateStatus(message) {
        hideError();
        AJS.$(".save-status").empty();
        AJS.$(".save-status").append(message);
    }

    function showError(message) {
        AJS.$("#errorMessage").append("<div class='aui-message aui-message-error error'>" + message + "</div>");
    }

    function hideError() {
        AJS.$("#errorMessage").empty();
    }


AJS.$(document).ready(function() {
    updateStatus("Loading...");
    processResource(processMappings, "");
    processResource(processValidClusters, "cluster/valid");
    processResource(processConfig, "config");
});


