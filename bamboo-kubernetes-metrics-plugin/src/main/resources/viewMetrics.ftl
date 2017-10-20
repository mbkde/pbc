[#-- @ftlvariable name="action" type="com.atlassian.bamboo.ww2.actions.chains.ArtifactUrlRedirectAction" --]
[#-- @ftlvariable name="" type="com.atlassian.bamboo.ww2.actions.chains.ArtifactUrlRedirectAction" --]

<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.result">
    <title>PBC Container Metrics</title>
    <meta name="tab" content="PBC Metrics"/>
</head>

<body>
<script src="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/d3_v2.js"></script>
<script src="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/rickshaw.js"></script>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.6.2/jquery.min.js"></script>
<link type="text/css" rel="stylesheet" href="https://code.shutterstock.com/rickshaw/src/css/graph.css">
<link type="text/css" rel="stylesheet" href="https://code.shutterstock.com/rickshaw/src/css/detail.css">
<h1>PBC Container Metrics</h1>
Shows CPU and memory unitization of PBC containers used in the build. If absent, the metrics were likely not generated or data is missing.
[#list containerList.iterator() as containerName]
    <h2>${containerName} container</h2>
    <h3>Memory usage</h3>
    <div id="${containerName}-memory-chart"></div>
    <h3>CPU usage</h3>
    <div id="${containerName}-cpu-chart"></div>
[/#list]

<script type="text/javascript">
[#list containerList.iterator() as containerName]
    var memoryGraph = new Rickshaw.Graph( {
        element: document.querySelector("#${containerName}-memory-chart"),
        renderer: 'line',
        series: [{"color": "steelblue", "data": ${memoryMap[containerName]}}],
        onData: function(d) { d[0].data[0].y = 80; return d },
        onComplete: function(transport) {
            var graph = transport.graph;
            var detail = new Rickshaw.Graph.HoverDetail({ graph: graph });
        },
    });
    var cpuGraph = new Rickshaw.Graph( {
        element: document.querySelector("#${containerName}-cpu-chart"),
        renderer: 'line',
        series: [{"color": "steelblue", "data": ${cpuMap[containerName]}}],
        onData: function(d) { d[0].data[0].y = 80; return d },
        onComplete: function(transport) {
            var graph = transport.graph;
            var detail = new Rickshaw.Graph.HoverDetail({ graph: graph });
        },
    });
    memoryGraph.render();
    cpuGraph.render();

[/#list]

</script>
</body>