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
