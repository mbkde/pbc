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


/**
 * expectations on side of the js code.
 * <input> with class 'docker-container-autocomplete' to add autocomplete to
 * <input> with class 'docker-extra-containers' to save and load the extra containers 
 * 
 */
dockerExtraImageEdit = false;
dockerExtraImageEditIndex = 0;

AJS.$(document).ready(function () {
    var knownImages = {};
    AJS.$.getJSON(AJS.contextPath() + "/rest/docker-ui/1.0/ui/knownImages", function (data) {
        knownImages = data;
        AJS.$(".docker-container-autocomplete").autocomplete({
            minLength: 0,
//                    position: { my : "right top", at: "right bottom" },
            source: knownImages
        }
        );
    });
    generateExtraContainersForJson();
    AJS.$("button#docker_addExtraImage").click(function () {
        dockerExtraImageEdit = false;
        dockerExtraImageEditIndex = 0;
        AJS.$("input#dockerExtraImage-name").val("");
        AJS.$("input#dockerExtraImage-image").val("");
        AJS.dialog2("#dockerExtraImage-dialog").show();
    });
    AJS.$("button#dockerExtraImage-dialog-submit-button").click(function () {
        var newone = {};
        newone.name = AJS.$("input#dockerExtraImage-name").val();
        newone.image = AJS.$("input#dockerExtraImage-image").val();
        newone.size = AJS.$("select#dockerExtraImage-size").val();

        AJS.dialog2("#dockerExtraImage-dialog").hide();
        var json = getExtraContainersData();
        if (dockerExtraImageEdit === true) {
            json[dockerExtraImageEditIndex] = newone;
        } else {
            json.push(newone);
        }
        updateExtraContainersData(json);
        drawTable(json);
    });
    AJS.$("button#dockerExtraImage-dialog-close-button").click(function () {
        AJS.dialog2("#dockerExtraImage-dialog").hide();
    });
});

function getExtraContainersData() {
    var text = AJS.$("input.docker-extra-containers").val();
    var json;
    try {
        json = JSON.parse(text);
    } catch (e) {
        json = [];
    }
    if ([].constructor === json.constructor) {
        return json;
    }
    return [];

}


function updateExtraContainersData(json) {
    AJS.$("input.docker-extra-containers").val(JSON.stringify(json));
}

function generateExtraContainersForJson() {
    var json = getExtraContainersData();
    drawTable(json);
}

function drawTable(data) {
    var table = AJS.$("#dockerImageTable tbody");
    table.find("tr:gt(0)").remove();
    AJS.$.each(data, function (i, mapping) {
        appendTableRow(table, mapping, i);
    });
}

function appendTableRow(parent, item, index) {
    parent.append('<tr id="row-revision-' + index + '">' +
            "<td>" + item.name + "</td>" +
            "<td>" + item.image + "</td>" +
            "<td>" + extraImageSizeToUI(item.size) + "</td>" +
            '<td>' +
            '<button type="button" class="aui-button" onclick="editExtraImage(' + index + ')">Edit</button>' +
            '<button type="button" class="aui-button" onclick="deleteExtraImage(' + index + ')">Delete</button>' +
            "</td>" +
            "</tr>");
}

function extraImageSizeToUI(size) {
    if (size.toUpperCase() === 'REGULAR') {
        return "Regular (~ 2G)";
    }
    if (size.toUpperCase() === 'SMALL') {
        return "Small (~ 1G)";
    }
    return size;
}

function deleteExtraImage(index) {
    var json = getExtraContainersData();
    json.splice(index, 1);
    updateExtraContainersData(json);
    drawTable(json);
}

function editExtraImage(index) {
    var json = getExtraContainersData();
    var val = json[index];
    AJS.$("input#dockerExtraImage-name").val(val.name);
    AJS.$("input#dockerExtraImage-image").val(val.image);
    AJS.$("select#dockerExtraImage-size").val(val.size);

    dockerExtraImageEdit = true;
    dockerExtraImageEditIndex = index;
    AJS.dialog2("#dockerExtraImage-dialog").show();
}

