<div id="isolatedDockerExtra" class="field-group">
    <a id='docker_addExtraImage' class='aui-link'>Add Linked Docker Container</a>
    <label for="dockerImageTable" id="fieldLabelArea_updateMiscellaneous_dockerImageTable">Additional containers to run</label>
    <table id="dockerImageTable" class="aui">
        <tr>
            <th>Name</th>
            <th>Image</th>
            <th>Details</th>
            <th></th>
        </tr>
    </table>
    <div class="description" id="updateMiscellaneous_custom_isolated_docker_extraContainersDescription">
        Additional containers to spin up next to the main one. 
        Container's name becomes hostname inside the build. eg http://selenium:4444 for a container named 'selenium'
        <p>Based on the backend implementation, there will be different networking stack with different limitations. 
        For details, see <a href="https://bitbucket.org/atlassian/per-build-container/src/master/extra-containers.md">plugin documentation</a><p/>
    </div>
</div>
