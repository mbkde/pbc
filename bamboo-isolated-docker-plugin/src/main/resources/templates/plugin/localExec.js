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
    var jobKey = AJS.$('meta[name=jobKey]').attr("content");
    AJS.$("#generate").click(function() {
        var div = AJS.$("#docker-compose");
        div.empty();
        div.append("<p>Loading...</p>");
        var query = "";
        if (AJS.$('#dind').is(":checked"))
        {
            query = query + '?dind=' +  AJS.$('#dind').val();
        }
        AJS.$.get(AJS.contextPath() + "/rest/docker-ui/1.0/localExec/" + jobKey + query, function( data ) {
            div.empty();
            div.append("<textarea>" + data +"</textarea>");
        }, "text")
        .fail(function() {
            div.empty();
            AJS.$("#docker-compose").append("<p>Error while loading usages.</p>");
        });

    });
});
