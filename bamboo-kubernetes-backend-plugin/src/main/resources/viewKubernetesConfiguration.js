/*
 * Copyright 2017 Atlassian Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
define('feature/kubernetes-backend-plugin/config', [
    'jquery',
    'aui'
], (
    $,
    AJS
) => {
    'use strict';

    var restEndpoint = `${AJS.contextPath()}/rest/pbc-kubernetes/latest/`;


    function processResource(callback, relativeEndpoint) {
        $.ajax({
            type: 'GET',
            url: restEndpoint + relativeEndpoint,
            success: function (text) {
                callback(text);
                loadComplete();
            },
            error: function (jqXHR, textStatus, errorThrown) {
                showError('An error occurred while attempting to save:\n\n' + textStatus + '\n' +
                    errorThrown + '\n' + jqXHR.responseText);
                loadComplete();
            }
        });
    }

    function processConfig(response) {
        updateStatus('');
        $('#setRemoteConfig_sidekickToUse').val(response.sidekickImage);
        $('#setRemoteConfig_currentContext').val(response.currentContext);
        $('#setRemoteConfig_podTemplate').val(response.podTemplate);
        $('#setRemoteConfig_architecturePodConfig').val(response.architecturePodConfig);
        $('#setRemoteConfig_containerSizes').val(response.containerSizes);
        $('#setRemoteConfig_podLogsUrl').val(response.podLogsUrl);
        $("#setRemoteConfig_artifactoryCacheAllowList").val(response.artifactoryCacheAllowList);
        $("#setRemoteConfig_artifactoryCachePodSpec").val(response.artifactoryCachePodSpec);
        updateClusterRegistry(response);
        updateAWSSpecificFields(response);
        $('#setRemoteConfig_save').removeAttr('disabled');
    }

    function updateStatus(message) {
        hideError();
        $('.save-status').empty().append(message);
    }

    function showError(message) {
        $('#errorMessage').append(`<div class=\'aui-message aui-message-error error\'>${message}'</div>`);
    }

    function hideError() {
        $('#errorMessage').empty();
    }

    function updateClusterRegistry(response) {
        $('#setRemoteConfig_useClusterRegistry')
            .prop('checked', response.useClusterRegistry)
            .trigger('change');
        $('#setRemoteConfig_clusterRegistryAvailableSelector').val(response.clusterRegistryAvailableSelector);
        $('#setRemoteConfig_clusterRegistryPrimarySelector').val(response.clusterRegistryPrimarySelector);
    }

    function updateAWSSpecificFields(response) {
        if (response.showAwsSpecificFields) {
            $('#setRemoteConfig_iamRequestTemplate').val(response.iamRequestTemplate);
            $('#setRemoteConfig_iamSubjectIdPrefix').val(response.iamSubjectIdPrefix);
        }
        $('#setRemoteConfig_showAwsSpecificFields').val(response.showAwsSpecificFields);
    }

    function loadComplete() {
        $('#setRemoteConfig_save').removeAttr('disabled');
        $('#load_complete').val('true');
    }

    return {
        saveRemoteConfig: (e) => {
            e.preventDefault();
            var config = {};
            config.sidekickImage = $('#setRemoteConfig_sidekickToUse').val().trim();
            config.currentContext = $('#setRemoteConfig_currentContext').val().trim();
            config.podTemplate = $('#setRemoteConfig_podTemplate').val().trim();
            config.architecturePodConfig = $('#setRemoteConfig_architecturePodConfig').val().trim();
            if ($('#setRemoteConfig_showAwsSpecificFields').val() === 'true') {
                config.iamRequestTemplate = $('#setRemoteConfig_iamRequestTemplate').val().trim();
                config.iamSubjectIdPrefix = $('#setRemoteConfig_iamSubjectIdPrefix').val().trim();
            }
            config.containerSizes = $('#setRemoteConfig_containerSizes').val().trim();
            config.podLogsUrl = $('#setRemoteConfig_podLogsUrl').val().trim();
            config.useClusterRegistry = $('#setRemoteConfig_useClusterRegistry').is(':checked');
            config.clusterRegistryAvailableSelector = $('#setRemoteConfig_clusterRegistryAvailableSelector').val().trim();
            config.clusterRegistryPrimarySelector = $('#setRemoteConfig_clusterRegistryPrimarySelector').val().trim();
            config.artifactoryCacheAllowList = AJS.$("#setRemoteConfig_artifactoryCacheAllowList").val().trim();
            config.artifactoryCachePodSpec = AJS.$("#setRemoteConfig_artifactoryCachePodSpec").val().trim();


            updateStatus('Saving...');

            $.ajax({
                type: 'POST',
                url: `${restEndpoint}config`,
                contentType: 'application/json',
                data: JSON.stringify(config),
                success: function () {
                    updateStatus('Saved');
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    updateStatus('');
                    showError('An error occurred while attempting to save:\n\n' + textStatus + '\n' +
                        errorThrown + '\n' + jqXHR.responseText);
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
