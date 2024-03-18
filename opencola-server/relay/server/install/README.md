<img src="../../../../img/pull-tab.svg" width="150" />

# Relay Server Deployment

## Install 

To build / install the relay server simply run:

```shell
> ./install
```

## Identity

In order to deploy a relay server, you will need to create a server identity and specify a root identity that can administer the server. These identities are managed by the [`ocr`](../../cli/README.md) tool. To get the root identity:

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


If you use the file as is, you'll need to create a storage directory where undelivered messagese are stored and events are logged. You'll also need to set environment variables for theserver and root identities:

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

# Running AWS

The relay server found at `relay.opencola.net` is running inside AWS using ECS (Elastic Container Service) that allows for deployment of Docker containers to the cloud. The following instructions describe how to set things up in AWS. 

> NOTE: Ideally deployment would be done with a deployment tool like [AWS Copilot](https://aws.github.io/copilot-cli/). Unfortunately, the relay server does not seem to pass AWS health checks when running on port 80, even though it works fine on port 80 on a dev box, and copilot does not support running on any port other than 80. For the time being, then, we deploy in a more clunky, manual way. 

It is beyond the scope of this documentation to describe all steps involved in setting up an Amazon account. If you're unfamiliar with AWS, and ECS in particular, please read [Getting Started with Amazon ECS](https://aws.amazon.com/ecs/getting-started/).


## AWS CLI

In order to push docker images to ECR (Elastic Container Repository) that can be deployed to ECS, you need to install the [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html):

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
## Creating an Image Repository for the Environment

* Got to [ECR Console](https://us-west-2.console.aws.amazon.com/ecr/repositories?region=us-west-2) 
* Make sure you are in the region you want to deploy to.
* Create a repository for the environment, with a name something like oc-relay-ENV, where ENV is the environment name (e.g. dev, prod, etc.)
* All other settings can be left as default.
* Note the repository URI (will be used as ECR_BASE_URI below)

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

## Optional - Create an EFS file system

> NOTE: You will likley want to skip this step and let the relay store files in Docker ephemeral storage. EFS is not cheap.

* Got to [EFS Console](https://us-west-2.console.aws.amazon.com/efs/home?region=us-west-2#/file-systems) and create a repository for the environment. Make sure to select the region you want to deploy to.
* Create a new file system with a name like oc-relay-ENV, where ENV is the environment name (e.g. dev, prod, etc.) 
* Note the EFS Id.


## Create a Task Definition

Below is a task definition template. Save it in a file called ```task-definition.json``` and replace ENV, IMAGE_URI, SERVER_PUBLIC_KEY_BASE58, SERVER_PRIVATE_KEY_BASE58, ROOT_ID_BASE58, REGION. If you're using EFS, uncommnent  `mountPoints` and `volumes` and replace EFS_ID. If you're not using EFS, remove the commented sections - comments are not valid in JSON.

```json
{
  "family": "oc-relay-ENV",
  "containerDefinitions": [
    {
      "name": "relay",
      "image": "IMAGE_URI",
      "cpu": 0,
      "portMappings": [
        {
          "name": "relay-2652-tcp",
          "containerPort": 2652,
          "hostPort": 2652,
          "protocol": "tcp",
          "appProtocol": "http"
        }
      ],
      "essential": true,
      "environment": [
        {
          "name": "relay.security.publicKeyBase58",
          "value": "SERVER_PUBLIC_KEY_BASE58"
        },
        {
          "name": "relay.security.privateKeyBase58",
          "value": "SERVER_PRIVATE_KEY_BASE58"
        },
        {
          "name": "relay.security.rootIdBase58",
          "value": "ROOT_ID_BASE58"
        }
      ],
      "environmentFiles": [],
      /* Uncomment this if you're using EFS. OTHERWISE REMOVE THIS COMMENT SECTION 
      "mountPoints": [
        {
          "sourceVolume": "relay-volume",
          "containerPath": "/var/relay",
          "readOnly": false
        }
      ],
      */
      "volumesFrom": [],
      "ulimits": [],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-create-group": "true",
          "awslogs-group": "/ecs/oc-relay-ENV",
          "awslogs-region": "REGION",
          "awslogs-stream-prefix": "ecs"
        },
        "secretOptions": []
      }
    }
  ],
  "executionRoleArn": "arn:aws:iam::APP_ID:role/ecsTaskExecutionRole",
  "networkMode": "awsvpc",
  /* Uncomment this if you're using EFS. OTHERWISE REMOVE THIS COMMENT SECTION 
  "volumes": [
    {
      "name": "relay-volume",
      "efsVolumeConfiguration": {
        "fileSystemId": "EFS_ID",
        "rootDirectory": "/"
      }
    }
  ], 
  */
  "requiresCompatibilities": [
    "FARGATE"
  ],
  "cpu": "512",
  "memory": "1024",
  "runtimePlatform": {
    "cpuArchitecture": "X86_64",
    "operatingSystemFamily": "LINUX"
  }
}
```

Now you're ready to create the definition in ECS:

* Got to [ECS Console](https://us-west-2.console.aws.amazon.com/ecs/home?region=us-west-2#/taskDefinitions) and create a new task definition.
* Choose "Create new Task Definition", then "Create new Task Definition JSON"
* Use the contents of```task-definition.json```

## Create a Cluster

* Got to [ECS Console](https://us-west-2.console.aws.amazon.com/ecs/home?region=us-west-2#/clusters) 
* Click Create Cluster
* Name it something like oc-ENV, where ENV is the environment name (e.g. dev, prod, etc.)
* Make sure AWS Fargate (serverless) is checked

## Create a Service

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