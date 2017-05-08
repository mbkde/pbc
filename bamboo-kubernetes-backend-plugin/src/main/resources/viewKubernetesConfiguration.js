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
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    showError(textStatus + " " + errorThrown);
                }
            });
    }

    function processConfig(response) {
        updateStatus("");
        AJS.$("#serviceUrl").val(response.serverUrl);
        AJS.$("#sidekickToUse").val(response.sidekickImage);
        AJS.$("#roleToUse").val(response.awsRole);
    }

    function setRemoteConfig() {
        var config = {};
        config.sidekickImage = AJS.$("#sidekickToUse").val().trim();
        config.awsRole = AJS.$("#roleToUse").val().trim();
        config.serverUrl = AJS.$("#serviceUrl").val().trim();

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
    processResource(processConfig, "config");
});


