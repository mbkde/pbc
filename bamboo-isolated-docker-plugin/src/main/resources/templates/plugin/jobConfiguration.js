/* 
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
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

pbcConfig = {
    dockerExtraImageEdit : false,
    dockerExtraImageEditIndex : 0,
    extraContainersDialogButtons : function()  {
        AJS.$("#docker_addExtraImage").click(pbcConfig.addExtraImage);
        AJS.$("button#dockerExtraImage-dialog-submit-button").click(function () {
            var newone = {};
            newone.name = AJS.$("input#dockerExtraImage-name").val();
            newone.image = AJS.$("input#dockerExtraImage-image").val();
            newone.size = AJS.$("select#dockerExtraImage-size").val();
            newone.commands = [];
            AJS.$("input.dockerExtraImage-command").each(function () {
               newone.commands.push(AJS.$(this).val()); 
            });
            newone.envVars = [];
            AJS.$("div.dockerExtraImage-envVar").each(function () {
               var env = {};
               env.name = AJS.$(this).find(".dockerExtraImage-envkey").val();
               env.value = AJS.$(this).find(".dockerExtraImage-envval").val();
               newone.envVars.push(env); 
            });

            AJS.dialog2("#dockerExtraImage-dialog").hide();
            var json = pbcConfig.getExtraContainersData();
            if (pbcConfig.dockerExtraImageEdit === true) {
                json[pbcConfig.dockerExtraImageEditIndex] = newone;
            } else {
                json.push(newone);
            }
            pbcConfig.updateExtraContainersData(json);
            pbcConfig.drawTable(json);
        });
        AJS.$("button#dockerExtraImage-dialog-close-button").click(function () {
            AJS.dialog2("#dockerExtraImage-dialog").hide();
        });
        AJS.$('#dockerExtraImage-commandsAdd').click(function() {
            pbcConfig.appendExtraContainerCommandToDialog('');
        });
        AJS.$('#dockerExtraImage-envAdd').click(function() {
            pbcConfig.appendExtraContainerEnvVarToDialog('', '');
        });
    },

    appendExtraContainerCommandToDialog : function(value) {
        AJS.$('#dockerExtraImage-commands').append("<div><input class='text dockerExtraImage-command' type='input' value='" + value +  "' name='dockerExtraImage-image'/><a class='aui-link' onclick='pbcConfig.removeLine(this)'>Remove</a><br/></div>");
    },

    appendExtraContainerEnvVarToDialog : function(key, value) {
        AJS.$('#dockerExtraImage-envVars').append("<div class='dockerExtraImage-envVar'><input class='text dockerExtraImage-envkey' type='input' name='dockerExtraImage-envkey' value='" + key + "'/>=<input class='text dockerExtraImage-envval' type='input' name='dockerExtraImage-envval' value='" + value + "'/><a class='aui-link' onclick='pbcConfig.removeLine(this)'>Remove</a><br/></div>");
    },

    removeLine : function(element) {
        var par = element.parentElement;
        par.parentElement.removeChild(par);
    },


    getExtraContainersData : function() {
        var text = AJS.$("input.docker-extra-containers").val();
        var json;
        try {
            json = JSON.parse(text);
        } catch (e) {
            json = [];
        }
        return pbcConfig.toExtraContainerArray(json);
    },

    toExtraContainerArray : function(json) {
        if (json && [].constructor === json.constructor) {
            return json;
        }
        return [];
    },


    updateExtraContainersData : function(json) {
        AJS.$("input.docker-extra-containers").val(JSON.stringify(json));
    },

    generateExtraContainersForJson : function() {
        var json = pbcConfig.getExtraContainersData();
        pbcConfig.drawTable(json);
    },

    drawTable : function(data) {
        var table = AJS.$("#dockerImageTable tbody");
        table.find("tr:gt(0)").remove();
        AJS.$.each(data, function (i, mapping) {
            pbcConfig.appendTableRow(table, mapping, i);
        });
    },

    appendTableRow : function(parent, item, index) {
        parent.append('<tr id="row-revision-' + index + '">' +
                "<td>" + item.name + "</td>" +
                "<td>" + item.image + "</td>" +
                "<td>" + pbcConfig.extraImageDetails(item) + "</td>" +
                '<td>' +
                '<button type="button" class="aui-button" onclick="pbcConfig.editExtraImage(' + index + ')">Edit</button>' +
                '<button type="button" class="aui-button" onclick="pbcConfig.deleteExtraImage(' + index + ')">Delete</button>' +
                "</td>" +
                "</tr>");
    },

    extraImageDetails : function(item) {
        var size = item.size;
        var sizeUI;
        if (size.toUpperCase() === 'REGULAR') {
            sizeUI = "<p>Regular size (~ 2G)</p>";
        }
        else if (size.toUpperCase() === 'SMALL') {
            sizeUI = "<p>Small size (~ 1G)</p>";
        }
        else if (size.toUpperCase() === 'LARGE') {
            sizeUI = "<p>Large size (~ 4G)</p>";
        }
        else if (size.toUpperCase() === 'XLARGE') {
            sizeUI = "<p>X Large size (~ 8G)</p>";
        }
        else if (size.toUpperCase() === 'XXLARGE') {
            sizeUI = "<p>XX Large size (~ 12G)</p>";
        }
        var envvarsUI = "";
        var commandsUI = "";
        var commands = pbcConfig.toExtraContainerArray(item.commands);
        if (commands.length > 0) {
            commandsUI = "<p>Commands:";
            AJS.$.each(commands, function(index, item) {
                commandsUI = commandsUI + " " + item;
            });
            commandsUI = commandsUI + "</p>";
        }
        var envvars = pbcConfig.toExtraContainerArray(item.envVars);
        if (envvars.length > 0) {
            envvarsUI = "<p>Environment Variables:<br/>";
            AJS.$.each(envvars, function(index, item) {
                envvarsUI = envvarsUI + item.name + "=" + item.value + "<br/>";
            });
            envvarsUI = envvarsUI + "</p>";
        }
        return sizeUI + commandsUI + envvarsUI;
    },

    deleteExtraImage : function(index) {
        var json = pbcConfig.getExtraContainersData();
        json.splice(index, 1);
        pbcConfig.updateExtraContainersData(json);
        pbcConfig.drawTable(json);
    },

    editExtraImage : function(index) {
        var json = pbcConfig.getExtraContainersData();
        var val = json[index];
        AJS.$("input#dockerExtraImage-name").val(val.name);
        AJS.$("input#dockerExtraImage-image").val(val.image);
        AJS.$("select#dockerExtraImage-size").val(val.size);
        AJS.$("div#dockerExtraImage-commands").empty();
        AJS.$.each(pbcConfig.toExtraContainerArray(val.commands), function(index, item) {
            pbcConfig.appendExtraContainerCommandToDialog(item);
        });
        AJS.$("div#dockerExtraImage-envVars").empty();
        AJS.$.each(pbcConfig.toExtraContainerArray(val.envVars), function(index, item) {
            pbcConfig.appendExtraContainerEnvVarToDialog(item.name, item.value);
        });

        pbcConfig.dockerExtraImageEdit = true;
        pbcConfig.dockerExtraImageEditIndex = index;
        AJS.dialog2("#dockerExtraImage-dialog").show();
    },

    addExtraImage : function() {
        AJS.$("input#dockerExtraImage-name").val("");
        AJS.$("input#dockerExtraImage-image").val("");
        AJS.$("div#dockerExtraImage-commands").empty();
        AJS.$("div#dockerExtraImage-envVars").empty();

        pbcConfig.dockerExtraImageEdit = false;
        pbcConfig.dockerExtraImageEditIndex = 0;
        AJS.dialog2("#dockerExtraImage-dialog").show();
    }
}
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
    pbcConfig.generateExtraContainersForJson();
    pbcConfig.extraContainersDialogButtons();

});

