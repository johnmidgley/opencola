
# Overview
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

# Check out CoPilot
https://aws.github.io/copilot-cli/docs/manifest/lb-web-service/
https://aws.github.io/copilot-cli/docs/developing/storage/#managed-efs
https://aws.github.io/copilot-cli/docs/developing/secrets/

# Repository Management

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

# Task Management

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

# Service Management

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

# Other



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