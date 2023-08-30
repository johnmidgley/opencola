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

```
docker build -t oc-relay .
docker tag oc-relay:latest 147892678753.dkr.ecr.us-west-2.amazonaws.com/oc-relay:latest
docker push 147892678753.dkr.ecr.us-west-2.amazonaws.com/oc-relay:latest
```

## [Create a Cluster](https://awscli.amazonaws.com/v2/documentation/api/latest/reference/ecs/create-cluster.html)

```
./create-cluster CLUSTER-NAME
```

## [Create a Service]()