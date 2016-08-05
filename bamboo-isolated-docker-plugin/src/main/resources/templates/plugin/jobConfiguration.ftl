${webResourceManager.requireResourcesForContext("docker.jobConfiguration")}

[@ui.bambooSection titleKey="isolated.docker.misc.header" descriptionKey='isolated.docker.misc.header.description']
    [@ww.checkbox labelKey='isolated.docker.enabled' toggle='true' name='custom.isolated.docker.enabled'/]
    [@ui.bambooSection dependsOn='custom.isolated.docker.enabled' showOn=true]
        [@ww.textfield cssClass='long-field' required=true labelKey='isolated.docker.image' name='custom.isolated.docker.image' descriptionKey="isolated.docker.image.description" /]
        [@ww.select labelKey='isolated.docker.size' name='custom.isolated.docker.imageSize'
            list=imageSizes listKey='first' listValue='second' cssClass="long-field"]
        [/@ww.select]
        [@ww.hidden cssClass='long-field' name='custom.isolated.docker.extraContainers'/]

        <div id="isolatedDockerExtra" class="field-group">
            <button id='docker_addExtraImage' class='aui-button' type='button'>Add Linked Docker Container</button>
            <label for="dockerImageTable" id="fieldLabelArea_updateMiscellaneous_dockerImageTable">Additional containers to run</label>
            <table id="dockerImageTable" class="aui">
                <tr>
                    <th>Name</th>
                    <th>Image</th>
                    <th>Size</th>
                    <th></th>
                </tr>
            </table>
            <div class="description" id="updateMiscellaneous_custom_isolated_docker_extraContainersDescription">
                Additional containers to spin up next to the main one. The Bamboo agent container gets linked to these. (--link) <p/>
                Container's name becomes hostname inside the build. eg http://selenium:4444 for a container named 'selenium'
            </div>
        </div>
    [/@ui.bambooSection]
[/@ui.bambooSection]

<!-- Render the extra docker image dialog -->
<section role="dialog" id="dockerExtraImage-dialog" class="aui-layer aui-dialog2 aui-dialog2-medium" aria-hidden="true">
    <!-- Dialog header -->
    <header class="aui-dialog2-header">
        <!-- The dialog's title -->
        <h2 class="aui-dialog2-header-main">Extra Docker container accessible from the agent.</h2>
        <!-- Close icon -->
        <a class="aui-dialog2-header-close">
            <span class="aui-icon aui-icon-small aui-iconfont-close-dialog">Cancel</span>
        </a>
    </header>
    <!-- Main dialog content -->
    <div class="aui-dialog2-content">
            <div class="aui">
                    <div class="field-group">
                        <label class="long-label" for="dockerExtraImage-name" id="dockerExtraImage-nameLabel">Name</label>
                        <input id="dockerExtraImage-name" class="text" type="input" name="dockerExtraImage-name">
                    </div>
                    <div class="field-group long-label">
                        <label class="long-label" for="dockerExtraImage-image" id="dockerExtraImage-imageLabel">Image</label>
                        <input id="dockerExtraImage-image" class="text" type="input" name="dockerExtraImage-image">
                    </div>
                    <div class="field-group long-label">
                        <label class="long-label" for="dockerExtraImage-size" id="dockerExtraImage-sizeLabel">Size</label>
                        <select name="dockerExtraImage-size" id="dockerExtraImage-size" class="select">
                                <option value="REGULAR" selected="selected">Regular (~2G memory)</option>
                                <option value="SMALL">Small (~1G memory)</option>
                        </select>
                    </div>
            </div>
    </div>
    <!-- Dialog footer -->
    <footer class="aui-dialog2-footer">
        <!-- Actions to render on the right of the footer -->
        <div class="aui-dialog2-footer-actions">
            <button id="dockerExtraImage-dialog-submit-button" class="aui-button aui-button-primary">OK</button>
            <button id="dockerExtraImage-dialog-close-button" class="aui-button aui-button-link">Cancel</button>
        </div>
        <!-- Hint text is rendered on the left of the footer -->
        <div class="aui-dialog2-footer-hint"></div>
    </footer>
</section>