
# Relay Server Deployment

## Install 

To build / install the relay server simply run:

```shell
> ./install
```

## Identity

In order to deploy a relay server, you will need to create a server identity and specify a root identity that can administer the server. These identities are managed by the `ocr` tool. To get the root identity:

```shell
> ocr identity
DBfSfvGwVvx89whkFoiuGeGcg6Et9GrmGRTwk8wLXUPj
```

To generate a server identity:

```shell
> ocr identity -s
publicKeyBase58: aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTJe91GYitakdPFHFqnQ3sw3siU1kHY94JQeY5pT2ErCz8FDUqXeaZ1kTzvP2SksygWPPmLvM1vDigew37YMaXv5ZD
privateKeyBase58: 2RxV4m78RGjpDZ5VV6TTtkSkRKgEC58qoYKq5rA4jkjPUbhnGZZrutfRjs15VRWc48KnwFDUYwPLgbSiGGBNgnuBih9s
```

The server private key is sensitive, so store it securely. 

# Running in Docker

A `docker-compose.yml` file is provider for running the relay server directly in Docker:

```yml
version: '3.8'

services:
  relay:
    build: .
    container_name: oc-relay
    ports:
      - "2652:2652"
    restart: unless-stopped
    environment:
      relay.server.port: 2652
      relay.server.callLogging: false
      relay.security.publicKeyBase58: ${OC_SERVER_PUBLIC_KEY}
      relay.security.privateKeyBase58: ${OC_SERVER_PRIVATE_KEY}
      relay.security.rootIdBase58: ${OC_ROOT_ID}

    # Comment this section out if you want storage to be inside the docker container 
    # in ephemeral storage
    volumes:
      - type: bind
        source: ./storage
        target: /var/relay # This should match storagePath in opencola-relay.yaml
```


If you use the file as is, you'll need to create a storage directory where undelivered messagese are stored. You'll also need to set environment variables for theserver and root identities:

```shell
> mkdir storage
> export OC_SERVER_PUBLIC_KEY=aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTJe91GYitakdPFHFqnQ3sw3siU1kHY94JQeY5pT2ErCz8FDUqXeaZ1kTzvP2SksygWPPmLvM1vDigew37YMaXv5ZD
> export OC_SERVER_PRIVATE_KEY=2RxV4m78RGjpDZ5VV6TTtkSkRKgEC58qoYKq5rA4jkjPUbhnGZZrutfRjs15VRWc48KnwFDUYwPLgbSiGGBNgnuBih9s
> export OC_ROOT_ID=DBfSfvGwVvx89whkFoiuGeGcg6Et9GrmGRTwk8wLXUPj
```

Then you can start the server:
```shell
$ docker-compose -p oc-relay up --build
```

# AWS
* [Setting up with Amazon ECR](https://docs.aws.amazon.com/AmazonECR/latest/userguide/get-set-up-for-amazon-ecr.html)
* [Using Amazon ECR with the AWS CLI](https://docs.aws.amazon.com/AmazonECR/latest/userguide/getting-started-cli.html)


## AWS CLI installation instructions ([source](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html))

```
$ curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"
$ sudo installer -pkg AWSCLIV2.pkg -target /
```

**To test:**

```
$ which aws
/usr/local/bin/aws
$ aws --version
aws-cli/2.4.5 Python/3.8.8 Darwin/18.7.0 botocore/2.4.5
```

**Create Credentials**
* Got to [IAM Console](https://us-west-2.console.aws.amazon.com/iamv2/home#/home) and follow [Set up credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-quickstart.html)

**Authenticate to default registry**

```
aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 147892678753.dkr.ecr.us-west-2.amazonaws.com
```

# Setting Up a Relay Server on AWS

NOTE: The goal is to automate all of this using AWS Copilot. It currently doesn't seem to work for
anything but port 80, so can't be used yet. For now, follow the manual instructions below that use
the AWS Management Console.

## Create an image repository for the environment

* Got to [ECR Console](https://us-west-2.console.aws.amazon.com/ecr/repositories?region=us-west-2) 
* Make sure you are in the region you want to deploy to.
* Create a repository for the environment, with a name something like oc-relay-ENV, where ENV is the environment name (e.g. dev, prod, etc.)
* All other settings can be left as default.
* Note the repository URI.

## Push the image to the repository

You will need to be logged in to the repository (change region as needed):

```
aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin ECR_BASE_URI
```

Then run the following commands to build and push the image:

```
./install
docker build -t oc-relay-ENV .
docker tag oc-relay-ENV:latest REPOSITORY_URI:latest
docker push REPOSITORY_URI:latest
```

## Create an EFS file system

* Got to [EFS Console](https://us-west-2.console.aws.amazon.com/efs/home?region=us-west-2#/file-systems) and create a repository for the environment. Make sure to select the region you want to deploy to.
* Create a new file system with a name like oc-relay-ENV, where ENV is the environment name (e.g. dev, prod, etc.) 
* Note the EFS Id.


## Create a Task Definition

* Got to [ECS Console](https://us-west-2.console.aws.amazon.com/ecs/home?region=us-west-2#/taskDefinitions) and create a new task definition.
* Choose "Create new Task Definition", then "Create new Task Definition JSON"
* Use the contents of```task-definition-template.json``` and edit ENV, IMAGE_URI, SERVER_PUBLIC_KEY_BASE58, SERVER_PRIVATE_KEY_BASE58, ROOT_ID_BASE58, REGION, and EFS_ID as appropriate.

# Create a Cluster

* Got to [ECS Console](https://us-west-2.console.aws.amazon.com/ecs/home?region=us-west-2#/clusters) 
* Click Create Cluster
* Name it something like oc-ENV, where ENV is the environment name (e.g. dev, prod, etc.)
* Make sure AWS Fargate (serverless) is checked

# Create a Service

* Got to [ECS Console](https://us-west-2.console.aws.amazon.com/ecs/home?region=us-west-2#/clusters)
* Click on the cluster you created above
* Click create

You will see a form that aligns with the following structure. Fill it out as follows:

```
Environment:
    Compute Configuration:
        Compute Options: Select "Launch Type"
        Launch type: FARGATE
        Platform version: LATEST
Deployment Configuration:
    Application Type: Service
    Family: oc-relay-ENV
    Service Name: oc-relay-prod-svc
    Service Type: Replica
    Number of tasks: 1
Networking:
    VPC: If you want to use a custom vpc, create it in the VPC section and select here.
    Security Group: 
        Select "Create new security group"
        Security group name: oc-relay-ENV-sg
        Security group description: oc-relay-ENV-sg
    Inbound rules for security groups:
        Type: Custom TCP
        Protocol: TCP
        Port range: 2652
        Source: Anywhere 
Load Balancing:
    Load Balancer:
        Load Balancer Type: Application Load Balancer
        Application Load Balancer: Create a new load balancer
        Load Balancer Name: oc-relay-ENV-lb
    Container:
        Listener Port:
            Select "Create new listener"
            Port: 2652
            Protocol: HTTP
        Target Group:
            Select "Create new target group"
            Target group name: oc-relay-ENV-tg

```

# Check out CoPilot
https://aws.github.io/copilot-cli/docs/manifest/lb-web-service/
https://aws.github.io/copilot-cli/docs/developing/storage/#managed-efs
https://aws.github.io/copilot-cli/docs/developing/secrets/

Does not seem to work on anything but port 80


# Appendix

## Repository Management

**Create repo**
```
aws ecr create-repository --repository-name oc-relay --image-scanning-configuration scanOnPush=true
```

**Check Repo**
```
aws ecr describe-repositories --repository-names oc-relay
```

**Install Cross Compilation tools**

[Cross compilation](https://docs.docker.com/build/building/multi-platform/#cross-compilation) is required to build the Docker image on a Mac. 

Wasn't able to get this to work on linux.

**Create Docker image**
```
> ./install
> docker build -t oc-relay .
```

**Tag Image**
```
docker tag oc-relay:latest 147892678753.dkr.ecr.us-west-2.amazonaws.com/oc-relay:latest
```

**Push Image**

*** ONLY DO THIS FROM LINUX ***

Likely to require authentication to default registry (above)

**For first push** - use ``push-to-aws`` thereafter. 
```
docker push 147892678753.dkr.ecr.us-west-2.amazonaws.com/oc-relay:latest
```

## Task Management

**Create Task Definition**

```
aws ecs register-task-definition --cli-input-json file://task-definition.json
```

**List Task Definitions**
```
aws ecs list-task-definitions
```

**Describe Task Definition**
```
aws ecs describe-task-definition --task-definition oc-relay-task-definition
```

## Service Management

**Create Cluster**
```
aws ecs create-cluster --cluster-name opencola
```


**Create Service**
```
aws ecs create-service --cli-input-json file://service-definition.json
```

**List Services**
```
aws ecs list-services --cluster opencola
```

**Describe Service**
```
aws ecs describe-services --cluster opencola --services oc-relay-service
```

## Other



**Update Service (Live Service)**

[Stack Overflow](https://stackoverflow.com/questions/48099941/how-to-update-container-image-in-aws-fargate)
 
```
aws ecs update-service --cluster opencola --service oc-relay-service --force-new-deployment
```

May need to manually delete old task. 

**[Delete image or repository](https://docs.aws.amazon.com/AmazonECR/latest/userguide/getting-started-cli.html)**

**Viewing Logs**
```
aws logs get-log-events --log-group-name /ecs/oc-relay-task-definition --log-stream-name ecs/oc-relay/29b75861031d4b1a9905f405a0bc343a --no-start-from-head
```