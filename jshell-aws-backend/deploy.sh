#!/usr/bin/env bash

cfn-lint -t infrastructure/template.yaml
sam validate -t infrastructure/template.yaml --region eu-west-2
sam validate -t infrastructure/template.yaml --lint
sam build -t infrastructure/template.yaml --parallel

sam deploy --stack-name "jshell" \
  --no-fail-on-empty-changeset \
  --no-confirm-changeset \
  --resolve-s3 \
  --s3-prefix "jshell" \
  --region "${AWS_REGION:-eu-west-2}" \
  --capabilities CAPABILITY_IAM \
  --template infrastructure/template.yaml
