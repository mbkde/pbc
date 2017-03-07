Secret management considerations
================================

As a general rule, one should not write secrets into docker images. With build agents
run as Docker containers one has to figure out a way for getting these secrets there.

* Use bamboo variables for some, requires user plan interactions with the variables.
* For user specific configuration files that contain secrets like ~/.m2/settings.xml ~/.netrc ~/.docker/config.json
that users expect to be present at build time, there are multiple options
    - [AWS KMS](https://aws.amazon.com/kms/)  and optionally a tool like [unicreds](https://github.com/Versent/unicreds)/[credstash](https://github.com/fugue/credstash) to pull secrets from
    - solutions like [vault](https://www.hashicorp.com/products/vault/) from Hashicorp

No matter what solution you pick, you should make the tools required available as part of the sidekick image and let
the users (people creating custom docker images for use in PBC) use this solution to pull any custom secret from the storage.

Overview of our own solution
============================

We use [unicreds](https://github.com/Versent/unicreds) binary that we install on the sidekick image under /buildeng folder.

We've create a wrapper script for use by our users to be forward compatible if we decide to use a different method.

Example `get-secret.sh` script that we copy to /buildeng folder on the sidekick.

```
#!/bin/sh
set -euf

AWS_REGION="${AWS_REGION:=us-east-1}"
/buildeng/unicreds -r $AWS_REGION get $1 | head -c -1
```

User docker images that require secrets will then include following snippets into the `/buildeng-custom/setup.sh` path (residing un user docker image)
that is called from the sidekick's own `/buildeng/run-agent.sh` script.

```
#entire file stored in secret
/buildeng/get-secret maven > ~/.m2/settings.xml

#just password stored in secret
DOCKER_AUTH=$(/buildeng/get-secret docker)
echo "{\"auths\":{\"https://docker.atlassian.com\":{\"auth\":\"${DOCKER_AUTH}\",\"email\":\"agent@atlassian.com\"}}}" > ~/.docker/config.json

# list of secrets stored as secret
first="yes"
for key in `/buildeng/get-secret gnupg-keys` ; do
    /buildeng/get-secret gnupg-${key} | gpg --allow-secret-key-import --import
    if [ ${first} = "yes" ]; then
        first="no"
        cat <<EOF > ~/.gnupg/gpg.conf
default-key ${key}
keyserver hkp://keys.gnupg.net
use-agent
EOF
    fi
done

```