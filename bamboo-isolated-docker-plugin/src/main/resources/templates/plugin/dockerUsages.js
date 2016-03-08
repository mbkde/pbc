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

AJS.$(document).ready(function () {
    var paramByName = function(name, url) {
        if (!url) url = window.location.href;
        name = name.replace(/[\[\]]/g, "\\$&");
        var regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)"),
            results = regex.exec(url);
        if (!results) return null;
        if (!results[2]) return '';
        return decodeURIComponent(results[2].replace(/\+/g, " "));
    };
    var image = paramByName('image', window.location.search);
    if (image === null) {
        //TODO show text field to enter image name
        AJS.$("#dockerImageDiv").append("<p>Missing ?image=[dockerImage] query in url.</p>");
    } else {
        AJS.$('span#dockerImage').append(image);
        AJS.$("#dockerImageDiv").append("<p>Loading...</p>");
        AJS.$.getJSON(AJS.contextPath() + "/rest/docker-ui/1.0/ui/usages?image=" + image, function( data ) {
            var div = AJS.$("#dockerImageDiv");
            div.empty();
            if (data.jobs === undefined || data.jobs.length === 0) {
                div.append("<p>Not used anywhere.</p>");
            }
            else
            {
                div.append('<table id="dockerImageTable" class="aui"><tbody id="dockerImageTableBody"><tr><th>Job using Docker Image</th></tr></tbody></table>');
                var table = AJS.$("#dockerImageTableBody");
                $.each(data.jobs, function(i, job) {
                    var line = '<tr><td><a href="' + AJS.contextPath() + '/build/admin/edit/editMiscellaneous.action?buildKey=' + job.key + '">' + job.name + '</a></td></tr>';
                    table.append(line);
                });
            }
        }).fail(function() {
            AJS.$("#dockerImageDiv").append("<p>Error while loading usages.</p>");
        });
    }
    
});
