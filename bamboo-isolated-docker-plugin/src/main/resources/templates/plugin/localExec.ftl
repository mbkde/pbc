<head xmlns="http://www.w3.org/1999/html">
    <meta name="decorator" content="atl.result">
    <title>How to reproduce build locally?</title>
    ${webResourceManager.requireResourcesForContext("viewLocalExec")}
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

<div id="docker-compose"</div>
<textarea>
version: '2'
services:
  bamboo-agent:
      image: ${configuration.dockerImage}
      working_dir: /buildeng/bamboo-agent-home/xml-data/build-dir
      entrypoint: /usr/bin/tail
      command: -f /dev/null
      <#if configuration.extraContainers?size != 0>
      links:
        <#list configuration.extraContainers as extra>
        - ${extra.name}
        </#list>
      </#if>
#     environment:
#        - SSH_AUTH_SOCK=$\{SSH_AUTH_SOCK}
      volumes:
         - .:/buildeng/bamboo-agent-home/xml-data/build-dir
         - ~/.m2/settings.xml:/root/.m2/settings.xml
         - ~/.docker/config.json:/root/.docker/config.json
         - ~/.ssh:/root/.ssh
         - ~/.gradle/gradle.properties:/root/.gradle/gradle.properties
         - ~/.gnupg/:/root/.gnupg/
         - ~/.npmrc:/root/.npmrc
#         - $\{SSH_AUTH_SOCK}:$\{SSH_AUTH_SOCK}

<#if configuration.extraContainers?size != 0>
  <#list configuration.extraContainers as extra>
  ${extra.name}:
     image: ${extra.image}
     <#if extra.commands?size != 0>
     command:
     <#list extra.commands as cmd>
       - ${cmd}
     </#list>
     </#if>
     <#if extra.envVariables?size != 0>
     environment:
     <#list extra.envVariables as env>
       - ${env.name}=${env.value}
     </#list>
     </#if>
     volumes:
         - .:/buildeng/bamboo-agent-home/xml-data/build-dir
  </#list>

</#if>
</textarea>

<h2>Execute the builds</h2>
<pre>docker-compose exec bamboo-agent /bin/bash</pre> for interactive shell.
<p/>
<pre>docker-compose exec bamboo-agent npm install</pre> for specific commands only.

</body>