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
                                <option value="XXXLARGE" selected="selected">XXXLarge (~6G memory)</option>
                                <option value="XXLARGE" selected="selected">XXLarge (~5G memory)</option>
                                <option value="XLARGE" selected="selected">XLarge (~4G memory)</option>
                                <option value="LARGE" selected="selected">Large (~3G memory)</option>
                                <option value="REGULAR" selected="selected">Regular (~2G memory)</option>
                                <option value="SMALL">Small (~1G memory)</option>
                        </select>
                    </div>
                    <div class="field-group">
                        <label class="long-label" for="dockerExtraImage-commands" id="dockerExtraImage-commandsLabel">Commands</label>
                        <a id='dockerExtraImage-commandsAdd' class='aui-link'>Add Command</a>
                        <div id="dockerExtraImage-commands" name="dockerExtraImage-commands"/>
                    </div>
                    <div class="field-group">
                        <label class="long-label" for="dockerExtraImage-env" id="dockerExtraImage-envLabel">Environment Variables</label>
                        <a id='dockerExtraImage-envAdd' class='aui-link'>Add Env Var</a>
                        <div id="dockerExtraImage-envVars" name="dockerExtraImage-envVars"/>
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
