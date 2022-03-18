/* 
 * Copyright 2017 Atlassian Pty Ltd.
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
   var restEndpoint = AJS.contextPath() + "/rest/pbc-kubernetes/latest/";
    function processResource(callback, relativeEndpoint) {
        AJS.$.ajax({
                type: 'GET',
                url: restEndpoint + relativeEndpoint,
                success: function (text) {
                    callback(text);
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    showError("An error occurred while attempting to save:\n\n" + textStatus + "\n" +
                        errorThrown + "\n" + jqXHR.responseText);                }
            });
    }

    function processConfig(response) {
        updateStatus("");
        AJS.$("#sidekickToUse").val(response.sidekickImage);
        AJS.$("#currentContext").val(response.currentContext);
        AJS.$("#podTemplate").val(response.podTemplate);
        AJS.$("#architecturePodConfig").val(response.architecturePodConfig);
        AJS.$("#iamRequestTemplate").val(response.iamRequestTemplate);
        AJS.$("#iamSubjectIdPrefix").val(response.iamSubjectIdPrefix);
        AJS.$("#containerSizes").val(response.containerSizes);
        AJS.$("#podLogsUrl").val(response.podLogsUrl);
        AJS.$("input#useClusterRegistry").prop('checked', response.useClusterRegistry);
        updateClusterRegistry();
        AJS.$("#clusterRegistryAvailableSelector").val(response.clusterRegistryAvailableSelector);
        AJS.$("#clusterRegistryPrimarySelector").val(response.clusterRegistryPrimarySelector);
    }

    function setRemoteConfig() {
        var config = {};
        config.sidekickImage = AJS.$("#sidekickToUse").val().trim();
        config.currentContext = AJS.$("#currentContext").val().trim();
        config.podTemplate = AJS.$("#podTemplate").val().trim();
        config.architecturePodConfig = AJS.$("#architecturePodConfig").val().trim();
        config.iamRequestTemplate = AJS.$("#iamRequestTemplate").val().trim();
        config.iamSubjectIdPrefix = AJS.$("#iamSubjectIdPrefix").val().trim();
        config.containerSizes = AJS.$("#containerSizes").val().trim();
        config.podLogsUrl = AJS.$("#podLogsUrl").val().trim();
        var checked = AJS.$("input#useClusterRegistry").is(":checked");
        config.useClusterRegistry = checked;
        config.clusterRegistryAvailableSelector = AJS.$("#clusterRegistryAvailableSelector").val().trim();
        config.clusterRegistryPrimarySelector = AJS.$("#clusterRegistryPrimarySelector").val().trim();


        updateStatus("Saving...");

        AJS.$.ajax({
            type: "POST",
            url: restEndpoint + "config",
            contentType: 'application/json',
            data: JSON.stringify(config),
            success: function () {
                updateStatus("Saved");
            },
            error: function (jqXHR, textStatus, errorThrown) {
                updateStatus("");
                showError("An error occurred while attempting to save:\n\n" + textStatus + "\n" +
                    errorThrown + "\n" + jqXHR.responseText);
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
    
    function updateClusterRegistry() {
        var checkbox = AJS.$("input#useClusterRegistry");
        if (checkbox.is(":checked")) {
            AJS.$(".dependsClusterRegistryShow").show();
        } else {
            AJS.$(".dependsClusterRegistryShow").hide();
        }
    }
    


AJS.$(document).ready(function() {
    updateStatus("Loading...");
    processResource(processConfig, "config");
});


