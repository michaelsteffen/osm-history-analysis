#!/bin/bash

echo "Building..."
if sbt assembly; then
    echo "Copying built jar to S3..."
    aws s3 cp ./target/scala-2.11/osm-history-analysis.jar s3://osm-history/osm-history-analysis.jar

    echo "Cleaning previous run from S3..."
    aws s3 rm s3://osm-history/planet-history --recursive

    echo "Creating cluster..."
    aws emr create-cluster \
      --name "OSM History Analysis Cluster (Planet)" \
      --tags "job=osm-history-planet" \
      --region us-east-1 \
      --ec2-attributes SubnetId=subnet-c84fda83,KeyName=OSMHistoryKey \
      --instance-groups file://./aws/instanceGroups-lg.json \
      --log-uri s3://osm-history/logs/planet/ \
      --use-default-roles \
      --configurations file://./aws/emrConfig-lg.json \
      --visible-to-all-users \
      --applications Name="Spark" Name="Ganglia" \
      --release-label emr-5.11.0 \
      --steps file://./aws/steps-planet.json \
      --auto-terminate
else
    echo "Build failed. Aborting cluster creation."
fi






