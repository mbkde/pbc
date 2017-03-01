<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.result">
    <title>How to reproduce build locally?</title>
    ${webResourceManager.requireResourcesForContext("viewLocalExec")}
    <meta name="jobKey" content="${jobKey}">
</head>

<body>
<h1>How to reproduce the Per-build Container (PBC) job build on local dev machine?</h1>

<h2>Requirements</h2>
<p>In order to reproduce you need the following tools installed on your machine:</p>
<ul>
<li>Latest docker</li>
<li>Latest docker-compose</li>
</ul>

<h2>Generate Docker Compose file</h2>
<br/>
Please note: If your bamboo-agent docker image is not running as root user, you will have to manually tweak volume mappings in the generated file.
<form>
  <input type="checkbox" id="reservations" name="reservations" value="true">Generate memory and CPU reservations<br>
  <input type="checkbox" id="mavenLocal" name="mavenLocal" value="true">Share Maven local repository (unchecked means only settings files are shared)<br>
  <#if dockerIncluded >
  <input type="checkbox" id="dind" name="dind" value="true">Use Docker in Docker (unchecked uses side by side Docker, not recommended for some usecases)<br>
  </#if>
  <input type="button" id="generate" class="aui-button aui-button-primary" value="Generate docker-compose.yaml">
</form>

<div id="docker-compose"></div>

<h2>Copy Docker Compose file</h2>
<p>Copy paste this generated docker-compose.yaml file. and store it at the root directory of the git checkout.</p>
<br/>


<h2>Start up docker containers</h2>
Run
<pre class='code'>docker-compose up</pre> at the directory where you stored the docker-compose.yaml file (the root of the git checkout)
This will start all the containers for you.

<h2>Execute the builds</h2>
Open new terminal and run:
<p/>
<pre class='code'>docker-compose exec bamboo-agent /bin/bash -l</pre> for interactive shell.
<p/>
<pre class='code'>docker-compose exec bamboo-agent npm install</pre> for specific commands only.

</body>