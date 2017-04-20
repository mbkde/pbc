[#-- @ftlvariable name="action" type="com.atlassian.bamboo.ww2.actions.chains.ArtifactUrlRedirectAction" --]
[#-- @ftlvariable name="" type="com.atlassian.bamboo.ww2.actions.chains.ArtifactUrlRedirectAction" --]

<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.result">
    <title>PBC Container Metrics</title>
    <meta name="tab" content="PBC Metrics"/>
</head>

<body>
<h1>PBC Container Metrics</h1>
<p>Shows CPU and memory unitization of PBC containers used in the build. If absent, the images were likely not generated or data is missing.</p>
[#list bambooAgentUrls.iterator() as url]
    <img src="${url}" alt="Container usage image"/>
[/#list]


<script type="text/javascript">

AJS.$(document).ready(function () {
});
</script>
</body>