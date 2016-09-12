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
var restEndpoint = AJS.contextPath() + "/rest/pbc-docker/latest/";


function setSimpleDocker() {
        var payload = {};
        payload.certPath = AJS.$("#docker-certs").val().trim();
        payload.apiVersion = AJS.$("#docker-api").val().trim();
        payload.url = AJS.$("#docker-url").val().trim();
        updateStatus("Saving...");
        AJS.$.ajax({
            type: "POST",
            url: restEndpoint + "config",
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function () {
                updateStatus("Saved.");
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
        AJS.$.ajax({
                type: 'GET',
                url: restEndpoint + "config",
                success: function (response) {
                    AJS.$("#docker-certs").val(response.certPath);
                    AJS.$("#docker-api").val(response.apiVersion);
                    AJS.$("#docker-url").val(response.url);
                    updateStatus("");
                },
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    updateStatus("");
                    showError(textStatus + " " + errorThrown);
                }

    });
});