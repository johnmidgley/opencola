# AWS CLI Scripts

## Installation
* [Setting up with Amazon ECR](https://docs.aws.amazon.com/AmazonECR/latest/userguide/get-set-up-for-amazon-ecr.html)
* [Using Amazon ECR with the AWS CLI](https://docs.aws.amazon.com/AmazonECR/latest/userguide/getting-started-cli.html)


### AWS CLI installation instructions ([source](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html))

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
* Got to [IAM Console](https://us-east-1.console.aws.amazon.com/iamv2/home#/home) and follow [Set up credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-quickstart.html)


## Login
Login, required before using other commands:
```
./login
```

## Repository

### [Create a Repository](https://awscli.amazonaws.com/v2/documentation/api/latest/reference/ecr/create-repository.html)
A Repository stores versions for a single image.

```
./create-repo REPO-NAME
```

### Push a Docker Image to a Repository

Example for pushing oc-relay :
> Using ```:latest``` tag. NOT recommended - should have explicit version

```
docker build -t oc-relay-v2 .
docker tag oc-relay:latest 147892678753.dkr.ecr.us-west-2.amazonaws.com/oc-relay-v2:latest
docker push 147892678753.dkr.ecr.us-west-2.amazonaws.com/oc-relay-v2:latest
```

### List images in repository

```
aws ecr describe-images --repository-name oc-relay-v2
```

## Clusters

### [Create a Cluster](https://awscli.amazonaws.com/v2/documentation/api/latest/reference/ecs/create-cluster.html)

```
./create-cluster CLUSTER-NAME
```

or 

```
aws ecs create-cluster --cluster-name opencola
```

### List CLusters

```
aws ecs list-clusters
```

## Services

### [Create a Service]()

### List Services (in a cluster)

```
aws ecs list-services --cluster opencola
```

### Describe services

```
aws ecs describe-services --cluster opencola --services oc-relay-service
```

## Tasks

### List Task Definitions

```
aws ecs list-task-definitions
```

### List tasks

```
aws ecs list-tasks --cluster opencola
```

### Describe tasks

```
aws ecs describe-tasks --cluster opencola --tasks 2cc64c7681964e208baa18c2f9d1c1d7
```
