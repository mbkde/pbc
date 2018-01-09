[#-- @ftlvariable name="action" type="com.atlassian.bamboo.ww2.actions.chains.ArtifactUrlRedirectAction" --]
[#-- @ftlvariable name="" type="com.atlassian.bamboo.ww2.actions.chains.ArtifactUrlRedirectAction" --]

<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.result">
    <title>PBC Container Metrics</title>
    <meta name="tab" content="PBC Metrics"/>
</head>

<body>
<script src="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/d3_v3.js"></script>
<script src="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/rickshaw.js"></script>
<link type="text/css" rel="stylesheet" href="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/rickshaw.css">
<link type="text/css" rel="stylesheet" href="${req.contextPath}/download/resources/com.atlassian.buildeng.bamboo-kubernetes-metrics-plugin:kubernetes-metrics-resources/graph.css">
<h1>PBC Container Metrics</h1>
Shows CPU and memory unitization of PBC containers used in the build. If absent, the metrics were likely not generated or data is missing. Look for an error at the very end of the build log: "Failed to execute plugin 'Retreive Container Metrics from Prometheus' with error: ...".
[#list containerList as container]
<h2>${container.name} container</h2>
<h3>Memory usage</h3>
<div class="chartContainer">
    <div class="yAxis" id="${container.name}-limit-line-memory"></div>
    <div class="yAxis" id="${container.name}-y-axis-memory"></div>
    <div class="chart" id="${container.name}-memory-chart"></div>
    <div class="legend" id="${container.name}-memory-chart-legend"></div>
</div>
<h3>CPU usage</h3>
<div class="chartContainer">
    <div class="yAxis" id="${container.name}-y-axis-cpu"></div>
    <div class="chart" id="${container.name}-cpu-chart"></div>
    <div class="legend" id="${container.name}-cpu-chart-legend"></div>
</div>
[/#list]

<script type="text/javascript">
var tickFormat = function(y) {
    var abs_y = Math.abs(y);
    if (abs_y >= 1000000000) { return y / 1000000000 + "GB" }
    else if (abs_y >= 1000000)    { return y / 1000000 + "MB" }
    else if (abs_y >= 1000)       { return y / 1000 + "KB" }
    else if (abs_y < 1 && abs_y > 0)  { return y.toFixed(2) }
    else if (abs_y === 0)         { return '' }
    else                      { return y }
};

var generateOldCPUGraph = function(containerName, cpuMetrics) {
    var cpuGraph = new Rickshaw.Graph( {
        element: document.querySelector("#" + containerName + "-cpu-chart"),
        renderer: 'line',
        interpolation: 'linear',
        series: [{"color": "steelblue", "name": "cpu", "data": cpuMetrics}],
    });    
    var xAxisCpu = new Rickshaw.Graph.Axis.Time( { graph: cpuGraph } );
    var yAxisCpu = new Rickshaw.Graph.Axis.Y( {
        graph: cpuGraph,
        orientation: 'left',
        tickFormat: Rickshaw.Fixtures.Number.formatKMBT,
        element: document.getElementById(containerName + '-y-axis-cpu'),
    } );
    var hoverDetailCpu = new Rickshaw.Graph.HoverDetail( {
        graph: cpuGraph,
        yFormatter: function(y) { return y.toFixed(2) + " cores" }
    } );
    cpuGraph.render();
}

var generateNewCPUGraph = function(containerName, cpuUserMetrics, cpuSystemMetrics) {
    var cpuGraph = new Rickshaw.Graph( {
        element: document.querySelector("#" + containerName + "-cpu-chart"),
        renderer: 'area',
        interpolation: 'linear',
        series: [
            {"color": "steelblue", "name": "user", "data": cpuUserMetrics},
            {"color": "red", "name": "system", "data": cpuSystemMetrics}
        ],
    });    
    var xAxisCpu = new Rickshaw.Graph.Axis.Time( { graph: cpuGraph } );
    var yAxisCpu = new Rickshaw.Graph.Axis.Y( {
        graph: cpuGraph,
        orientation: 'left',
        tickFormat: Rickshaw.Fixtures.Number.formatKMBT,
        element: document.getElementById(containerName + '-y-axis-cpu'),
    } );
    var hoverDetailCpu = new Rickshaw.Graph.HoverDetail( {
        graph: cpuGraph,
        yFormatter: function(y) { return y.toFixed(2) + " cores" }
    } );
    var legend = new Rickshaw.Graph.Legend({
        graph: cpuGraph,
        element: document.querySelector("#" + containerName + "-cpu-chart-legend")
    });
    var shelving = new Rickshaw.Graph.Behavior.Series.Toggle({
        graph: cpuGraph,
        legend: legend
    });

    cpuGraph.render();
}

var generateNewMemoryGraph = function(containerName, memorySwapMetrics, memoryRssMetrics, memoryCacheMetrics, memoryLimit) {
    // sort of a hack to get an array with the same structure and size and populate it with the limit
    var limit = JSON.parse(JSON.stringify(memorySwapMetrics));
    for (var i = 0; i < limit.length; i++) {
        limit[i]["y"] = memoryLimit * 1000000;
    }
    var memoryGraph = new Rickshaw.Graph( {
        element: document.querySelector("#" + containerName + "-memory-chart"),
        renderer: 'multi',
        interpolation: 'linear',
        series: [
            {"color": "steelblue", "name": "rss", renderer: "area", "data": memoryRssMetrics},
            {"color": "lightblue", "name": "cache", renderer: "area", "data": memoryCacheMetrics},
            {"color": "red", "name": "swap", renderer: "area", "data": memorySwapMetrics},
            {"color": "yellow", "name": "container limit", "renderer" : "line", "data" : limit}
        ],
    });

    var legend = new Rickshaw.Graph.Legend({
        graph: memoryGraph,
        element: document.querySelector("#" + containerName + "-memory-chart-legend")
    });
    var shelving = new Rickshaw.Graph.Behavior.Series.Toggle({
        graph: memoryGraph,
        legend: legend
    });
    generateMemoryGraphCommon(containerName, memoryGraph, memoryLimit);
}

var generateOldMemoryGraph = function(containerName, memoryMetrics, memoryLimit) {
    var memoryGraph = new Rickshaw.Graph( {
        element: document.querySelector("#" + containerName + "-memory-chart"),
        renderer: 'line',
        interpolation: 'linear',
        series: [{"color": "steelblue", "name": "memory", "data": memoryMetrics}],
    });
    generateMemoryGraphCommon(containerName, memoryGraph, memoryLimit);
}

var generateMemoryGraphCommon = function(containerName, memoryGraph, memoryLimit) {
    var xAxisMemory = new Rickshaw.Graph.Axis.Time( { graph: memoryGraph } );

    var yAxisMemory = new Rickshaw.Graph.Axis.Y( {
        graph: memoryGraph,
        orientation: 'left',
        tickFormat: tickFormat,
        element: document.getElementById(containerName + '-y-axis-memory'),
    } );

    var hoverDetailMemory = new Rickshaw.Graph.HoverDetail( {
        graph: memoryGraph,
        yFormatter: function(y) { return (y/1000000).toFixed(2) + " MB" }
    } );

    memoryGraph.render();
}

[#list containerList as container]

[#if container.cpuUserMetrics??]

generateNewCPUGraph("${container.name}", ${container.cpuUserMetrics}, ${container.cpuSystemMetrics});

[#else]

[#if container.cpuMetrics??]
generateOldCPUGraph("${container.name}", ${container.cpuMetrics});
[/#if]

[/#if]

[#if container.memoryRssMetrics??]

generateNewMemoryGraph("${container.name}", ${container.memorySwapMetrics}, ${container.memoryRssMetrics},
 ${container.memoryCacheMetrics}, ${container.memoryLimit});

[#else]

generateOldMemoryGraph("${container.name}", ${container.memoryMetrics}, ${container.memoryLimit});

[/#if]



[/#list]

</script>
</body>