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

/* global insertionQ*/

AJS.$(document).ready(function () {
    var path = window.location.pathname;
    var isRequirements = path.indexOf("build/admin/edit/defaultBuildRequirement.action") !== -1;
    if (isRequirements) {
        insertionQ('table.requirements-table').every(function (element) {
            var row = $("tr[data-key='system.isolated.docker']", element);
            $("td:eq(4)", row).replaceWith("<td><span class='aui-lozenge aui-lozenge-current aui-lozenge-subtle' original-title='Only Docker agents can build this job'>Docker Agents only</span></td>");
            $('div.requirements-info').replaceWith(
                    "<div class='aui-message aui-message-warning warning'><p class='title'>" + 
                    "<strong>This job is built by Docker Agents.</strong></p>" +
                    "<p>This job is built by Docker container based agent. </p><p>The information about capability availability is inaccurate because it's not known up front what capabilities the Docker image provides. </p><p> Please consult the creator of the image in question if in doubt.</p></div>");
                
        });
    }

});

