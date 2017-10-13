[#-- @ftlvariable name="action" type="com.atlassian.bamboo.ww2.actions.chains.ArtifactUrlRedirectAction" --]
[#-- @ftlvariable name="" type="com.atlassian.bamboo.ww2.actions.chains.ArtifactUrlRedirectAction" --]

<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.result">
    <title>PBC Container Metrics</title>
    <meta name="tab" content="PBC Metrics"/>
</head>

<body>
<script src="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/d3_v4.js"></script>
<script src="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/rickshaw.js"></script>
<h1>PBC Container Metrics</h1>
<p>Shows CPU and memory unitization of PBC containers used in the build. If absent, the metrics were likely not generated or data is missing.</p>
[#list bambooAgentUrls.iterator() as url]
    <p>"${url}"</p>
[/#list]


<script type="text/javascript">

AJS.$(document).ready(function () {
});
</script>
</body>