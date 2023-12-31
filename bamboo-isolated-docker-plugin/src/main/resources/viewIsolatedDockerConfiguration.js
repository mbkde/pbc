/*
 * Copyright 2018 Atlassian Pty Ltd.
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
define('feature/isolate-docker-plugin/config', [
    'jquery',
    'aui'
], (
    $,
    AJS
) => {
    'use strict';

    var restEndpoint = `${AJS.contextPath()}/rest/docker-ui/latest/`;

    function updateStatus(message) {
        hideError();
        $('.save-status').empty().append(message);
    }

    function hideError() {
        $('#errorMessage').empty();
    }

    function showError(message) {
        $('#errorMessage').append(`<div class='aui-message aui-message-error error'>${message}</div>`);
    }

    function processConfig(response) {
        updateStatus('');
        $('#enableSwitch').prop("checked", response.enabled);
        $('#setRemoteConfig_defaultImage').val(response.defaultImage);
        $('#setRemoteConfig_maxAgentCreationPerMinute').val(response.maxAgentCreationPerMinute);
        $('#setRemoteConfig_architectureConfig').val(response.architectureConfig);
        $('#setRemoteConfig_awsVendor').prop('checked', response.awsVendor);
        $('#setRemoteConfig_agentCleanupTime').val(response.agentCleanupTime);
        $('#setRemoteConfig_agentRemovalTime').val(response.agentRemovalTime);
    }

    function processResource(callback, relativeEndpoint) {
        $.ajax({
            type: 'GET',
            url: restEndpoint + relativeEndpoint,
            success: function (text) {
                callback(text);
                loadComplete();
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                showError(`An error occurred while attempting to read config:\n\n${textStatus}\n${errorThrown}\n${XMLHttpRequest.responseText}`);
                loadComplete();
            }
        });
    }

    function loadComplete() {
        $('#setRemoteConfig_save').removeAttr('disabled');
        $('#load_complete').val('true');
    }

    return {
        saveRemoteConfig: function (e) {
            e.preventDefault();
            const config = {};
            config.enabled = $('#enableSwitch').prop("checked");
            config.defaultImage = $('#setRemoteConfig_defaultImage').val().trim();
            config.maxAgentCreationPerMinute = $('#setRemoteConfig_maxAgentCreationPerMinute').val().trim();
            config.architectureConfig = $('#setRemoteConfig_architectureConfig').val().trim();
            config.awsVendor = $('#setRemoteConfig_awsVendor').is(':checked');
            config.agentCleanupTime = $('#setRemoteConfig_agentCleanupTime').val().trim();
            config.agentRemovalTime = $('#setRemoteConfig_agentRemovalTime').val().trim();

            updateStatus('Saving...');

            $.ajax({
                type: 'POST',
                url: restEndpoint + 'config',
                contentType: 'application/json',
                data: JSON.stringify(config),
                success: function () {
                    updateStatus('Saved');
                },
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    updateStatus('');
                    showError(`An error occurred while attempting to save:\n\n${textStatus}\n${errorThrown}\n${XMLHttpRequest.responseText}`);
                }
            });
        },

        onInit: function () {
            $('#setRemoteConfig_save')
                .on('click', this.saveRemoteConfig)
                .attr('disabled', 'disabled');
            updateStatus('Loading...');
            processResource(processConfig, 'config');
        }
    }
});
