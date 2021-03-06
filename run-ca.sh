#!/bin/bash

echo "Building..."
if sbt assembly; then
    echo "Copying built jar to S3..."
    aws s3 cp ./target/scala-2.11/osm-history-analysis.jar s3://osm-history/osm-history-analysis.jar

    echo "Cleaning previous run from S3..."
    aws s3 rm s3://osm-history/ca-history/ --recursive

    echo "Creating cluster..."
    aws emr create-cluster \
      --name "OSM History Analysis Cluster (CA) (125M partitions)" \
      --tags "job=osm-history-ca" \
      --region us-east-1 \
      --ec2-attributes SubnetId=subnet-c84fda83,KeyName=OSMHistoryKey \
      --instance-groups file://./aws/instanceGroups-sm.json \
      --log-uri s3://osm-history/logs/ca/ \
      --use-default-roles \
      --configurations file://./aws/emrConfig-sm.json \
      --visible-to-all-users \
      --applications Name="Spark" Name="Ganglia" \
      --release-label emr-5.11.0 \
      --steps file://./aws/steps-ca.json \
      --auto-terminate
else
    echo "Build failed. Aborting cluster creation."
fi




