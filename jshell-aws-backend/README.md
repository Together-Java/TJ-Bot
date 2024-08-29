# JShell AWS Backend
This module contains the infrastructure and code to create an AWS Lambda hosted JShell API. 
The API is used to evaluate Java code in AWS using JShell.

Sample request that can be made to this API:

```curl
curl -X POST "https://<FUNCTION_ID>.lambda-url.<AWS_REGION>>.on.aws/" \
     -H "Content-Type: application/json" \
     -d '{"code": "System.out.println(\"Hello, World\");"}'
```

## Getting Started
To use this project in your own AWS account, you first need an AWS account and an [AWS authenticated CLI](https://docs.aws.amazon.com/cli/v1/userguide/cli-chap-authentication.html).

### Required CLI tools
* [AWS ClI](https://aws.amazon.com/cli/) 
* [SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html)
* [cfn-lint](https://github.com/aws-cloudformation/cfn-lint) (this is for CloudFormation linting)

Once your terminal is all setup and authenticated, the easiest way to deploy this application is by running the `./deploy.sh`. 
This will compile and upload the project to AWS, it will then create a stack called "jshell" in your AWS account. The Lambda URL will be printed to the console.

If you prefer to deploy without the help of the `./deploy.sh` you can do it manually:
1. `cd infrastructure`
2. `sam build`
3. `sam deploy`

To delete the stack, this can be done either in CloudFormation via the AWS website or using the CLI command:
```bash
aws cloudformation delete-stack --stack-name jshell
```

## Testing Locally
To test the Lambda locally without the need to use an AWS account you can use the SAM Local to invoke the Lambda directly.

First build the project:
1. `cd infrastructure`
2. `sam build`

Now you can directly send events to the Lambda e.g.
```bash
echo '{"body": "{\"code\": \"System.out.println(\\\"Hello, World!\\\");\"}"}' | sam local invoke "CodeRunnerFunction" -e -
```
We pass `body` in this request unlike how we do in AWS because we need to match how AWS would send the request to the function.

**Note:** This requires [Docker](https://www.docker.com/) to be installed on your machine.

If you want to test locally using a web server (e.g. testing the integration with TJ Bot) you can run the `start-local.sh`

This will spin up a web server locally. To test you can use the following cURL:
```curl
curl -X POST http://127.0.0.1:3000/jshell \
     -H "Content-Type: application/json" \
     -d '{"code": "System.out.println(\"Hello, World!\");"}'
```
**Note:** This is using the SAM CLI web server, and it can be very slow to serve requests. 
This also requires [Docker](https://www.docker.com/) to be installed on your machine.
