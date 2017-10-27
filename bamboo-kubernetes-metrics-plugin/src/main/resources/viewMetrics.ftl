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
<link type="text/css" rel="stylesheet" href="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/rickshaw_graph.css">
<link type="text/css" rel="stylesheet" href="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/rickshaw_detail.css">
<link type="text/css" rel="stylesheet" href="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/graph.css">
<h1>PBC Container Metrics</h1>
Shows CPU and memory unitization of PBC containers used in the build. If absent, the metrics were likely not generated or data is missing. Look for an error at the very end of the build log: "Failed to execute plugin 'Retreive Container Metrics from Prometheus' with error: ...".
[#list containerList.iterator() as containerName]
<h2>${containerName} container</h2>
<h3>Memory usage</h3>
<div class="chartContainer">
    <div class="yAxis" id="${containerName}-y-axis-memory"></div>
    <div class="chart" id="${containerName}-memory-chart"></div>
</div>
<h3>CPU usage</h3>
<div class="chartContainer">
    <div class="yAxis" id="${containerName}-y-axis-cpu"></div>
    <div class="chart" id="${containerName}-cpu-chart"></div>
</div>
[/#list]

<script type="text/javascript">
[#list containerList.iterator() as containerName]
var memoryGraph = new Rickshaw.Graph( {
    element: document.querySelector("#${containerName}-memory-chart"),
    renderer: 'line',
    series: [{"color": "steelblue", "name": "memory", "data": ${memoryMap[containerName]}}],
});
var cpuGraph = new Rickshaw.Graph( {
    element: document.querySelector("#${containerName}-cpu-chart"),
    renderer: 'line',
    series: [{"color": "steelblue", "name": "cpu", "data": ${cpuMap[containerName]}}],
});

var xAxisMemory = new Rickshaw.Graph.Axis.Time( { graph: memoryGraph } );
var xAxisCpu = new Rickshaw.Graph.Axis.Time( { graph: cpuGraph } );

var yAxisMemory = new Rickshaw.Graph.Axis.Y( {
    graph: memoryGraph,
    orientation: 'left',
    tickFormat: function(y) {
        var abs_y = Math.abs(y);
        if (abs_y >= 1000000000) { return y / 1000000000 + "GB" }
        else if (abs_y >= 1000000)    { return y / 1000000 + "MB" }
        else if (abs_y >= 1000)       { return y / 1000 + "KB" }
        else if (abs_y < 1 && abs_y > 0)  { return y.toFixed(2) }
        else if (abs_y === 0)         { return '' }
        else                      { return y }
    },
    element: document.getElementById('${containerName}-y-axis-memory'),
} );
var yAxisCpu = new Rickshaw.Graph.Axis.Y( {
    graph: cpuGraph,
    orientation: 'left',
    tickFormat: Rickshaw.Fixtures.Number.formatKMBT,
    element: document.getElementById('${containerName}-y-axis-cpu'),
} );
var hoverDetailMemory = new Rickshaw.Graph.HoverDetail( {
    graph: memoryGraph,
    yFormatter: function(y) { return (y/1000000).toFixed(2) + " MB" }
} );
var hoverDetailCpu = new Rickshaw.Graph.HoverDetail( {
    graph: cpuGraph,
    yFormatter: function(y) { return y.toFixed(2) + " cores" }
} );


memoryGraph.render();
cpuGraph.render();

[/#list]

</script>
</body>