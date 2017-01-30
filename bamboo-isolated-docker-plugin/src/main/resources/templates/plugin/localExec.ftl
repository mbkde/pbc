<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.result">
    <title>How to reproduce build locally?</title>
    ${webResourceManager.requireResourcesForContext("viewLocalExec")}
    <meta name="jobKey" content="${jobKey}">
</head>

<body>
<h1>How to reproduce the build on local machine?</h1>

<h2>Requirements</h2>
<ul>
<li>Latest docker</li>
<li>Latest docker-compose</li>
</ul>

<h2>Docker-compose file</h2>
Copy paste this generated docker-compose.yaml file. and store it at the root directory of the git checkout.
<br/>
<br/>
<form>
  <input type="checkbox" id="reservations" name="reservations" value="true">Generate memory and CPU reservations<br>
  <input type="checkbox" id="mavenLocal" name="mavenLocal" value="true">Share Maven local repository (unchecked only settings files are shared)<br>
  <#if dockerIncluded >
  <input type="checkbox" id="dind" name="dind" value="true">Use Docker in Docker (unchecked uses side by side Docker)<br>
  </#if>
  <input type="button" id="generate" value="Generate">
</form>

<div id="docker-compose"></div>

<h2>Start up docker containers</h2>
<pre>docker-compose up</pre> at the directory where you stored the docker-compose.yaml file (the root of the git checkout)
This will start all the containers for you.

<h2>Execute the builds</h2>
Open new terminal and run:
<p/>
<pre>docker-compose exec bamboo-agent /bin/bash -l</pre> for interactive shell.
<p/>
<pre>docker-compose exec bamboo-agent npm install</pre> for specific commands only.

</body>