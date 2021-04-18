#!/bin/zsh

set -e

export AWS_ROLE_ARN=$(aws cloudformation list-exports | jq -r '.Exports[] | select(.Name=="KubernetesClusterAdminRole-RoleArn") | .Value')
export STS_CREDENTIALS=$(aws sts assume-role --role-arn $AWS_ROLE_ARN --role-session-name KubeconfigSession | jq -r '.Credentials') >/dev/null 2>&1
export AWS_ACCESS_KEY_ID_STS=$(echo $STS_CREDENTIALS | jq -r '.AccessKeyId') >/dev/null 2>&1
export AWS_SECRET_ACCESS_KEY_STS=$(echo $STS_CREDENTIALS | jq -r '.SecretAccessKey') >/dev/null 2>&1
export AWS_SESSION_TOKEN_STS=$(echo $STS_CREDENTIALS | jq -r '.SessionToken') >/dev/null 2>&1
export AWS_ACCESS_KEY_ID=$(echo $AWS_ACCESS_KEY_ID_STS) >/dev/null 2>&1
export AWS_SECRET_ACCESS_KEY=$(echo $AWS_SECRET_ACCESS_KEY_STS) >/dev/null 2>&1
export AWS_SESSION_TOKEN=$(echo $AWS_SESSION_TOKEN_STS) >/dev/null 2>&1
