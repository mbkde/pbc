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

/* global insertionQ, BAMBOO */

AJS.$(document).ready(function () {
    var path = window.location.pathname;
    var isJobTaskEdit = path.indexOf("/build/admin/edit/editBuildTasks.action") !== -1;
    if (isJobTaskEdit) {
        var indexedData = {};
        var update = function (cache) {
            if ($('.task-config').length) {
                var data = {};
                if (cache.enabled) { //0 is newly added task.. if docker is enabled, put it here to modify that ui as well.
                    data[0] = {};
                    data[0].label = "";
                    data[0].buildJdk = "";
                }
                $.each(cache.tasks, function (index, item) {
                    data[item.id] = item;
                });
                indexedData = data;
            }
        };
        var findTaskIndex = function () {
            var index = $("form#updateTask input#updateTask_taskId").attr("value");
            if (!index) {
                index = $("form#createTask input#createTask_taskId").attr("value");
            }
            return index;
        };
        insertionQ('select.builderSelectWidget').every(function (element) {
            var jq = $(element);
            var index = findTaskIndex();
            if (indexedData[index]) {
                jq.after("<div class='description' id='dockerBuilderDesc'><b>With Isolated docker enabled, this field is freeform. Please enter the value that the docker based agent provides</b></div>");
                jq.replaceWith("<input type='text' name='" + element.name + "' id='" + element.id + "' class='text long-field' value='" + indexedData[index].label + "'>");
                $("form a.addSharedBuilderCapability").remove();
            }
            return jq;
        });
        insertionQ('select.jdkSelectWidget').every(function (element) {
            var jq = $(element);
            var index = findTaskIndex();
            if (indexedData[index]) {
                jq.after("<div class='description' id='dockerJdkDesc'><b>With Isolated docker enabled, this field is freeform. Please enter the value that the docker based agent provides</b></div>");
                jq.replaceWith("<input type='text' name='" + element.name + "' id='" + element.id + "' class='text long-field' value='" + indexedData[index].buildJdk + "'>");
                $("form a.addSharedJdkCapability").remove();
            }
            return jq;
        });
        var doAjax = function () {
            AJS.$.ajax({
                type: 'GET',
                url: AJS.contextPath() + "/rest/docker-ui/1.0/ui/enabled?jobKey=" + BAMBOO.currentPlan.key,
                success: function (text) {
                    update(text);
                },
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    update({});
                }

            });

        };
        insertionQ('div#no-item-selected').every(function (element) {
            doAjax();
        });
        doAjax();
    }

});

