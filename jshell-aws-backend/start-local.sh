#!/usr/bin/env bash
sam build -t infrastructure/template.yaml --parallel
sam local start-api
