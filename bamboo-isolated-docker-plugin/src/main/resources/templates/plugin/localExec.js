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

/* global AJS */

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
    var jobKey = paramByName('jobKey', window.location.search);
    if (jobKey === null) {
        //TODO show text field to enter image name
        AJS.$("#docker-compose").append("<p>Missing ?jobKey query in url.</p>");
    } else {
        AJS.$("#docker-compose").append("<p>Loading...</p>");
        AJS.$.get(AJS.contextPath() + "/rest/docker/1.0/compose/" + jobKey, function( data ) {
            var div = AJS.$("#docker-compose");
            div.empty();
            div.html(data);
        }, "text")
        .fail(function() {
            AJS.$("#docker-compose").append("<p>Error while loading usages.</p>");
        });
    }
    
});
