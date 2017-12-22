Generate easily queryable OpenStreetMap change history using Spark. Uses OpenStreetMap ORC files generated with [osm2orc](https://github.com/mojodna/osm2orc).

This repo includes ORC-formatted data for Washington, DC to get you started. When you're ready to go global, you can use the world history file [hosted by AWS](https://aws.amazon.com/public-datasets/osm/).

## Building

Requires [sbt](https://www.scala-sbt.org/)

#### Test
```
sbt test
```

#### Build
```
sbt assembly
```

## Running Locally

Requires Spark 2.2.0

### Generating change data
```
spark-submit \
 --class com.michaelsteffen.osm.historyanalysis.App \
 --master local[4] \
 --driver-memory 5g \
 target/scala-2.11/osm-history-analysis.jar \git
 data/district-of-columbia.osh.orc \
 path/to/output.orc
```

### Querying in Spark

Start spark-shell:
```
spark-shell --jars target/scala-2.11/osm-history-analysis.jar
```

Import some things and load the data:
```
import org.apache.spark.sql.functions._
import com.michaelsteffen.osm.changes._

val changes = spark.read.orc("path/to/changes.orc").as[Change]
```

Count of new primary features by year:
```
changes
  .filter($"changeType" === ChangeUtils.FEATURE_CREATE)
  .groupBy(year($"timestamp"))
  .count
  .sort(desc("year(timestamp)"))
  .show()
```

Count of new buildings in 2017:
```
changes
  .filter(array_contains($"primaryFeatureTypes", "building"))
  .filter(year($"timestamp") === 2017)
  .filter($"changeType" === ChangeUtils.FEATURE_CREATE)
  .count
```

All changes for a specific feature, in order:
```
changes
  .filter($"primaryFeatureID" === "w226013371")
  .sort($"timestamp")
  .show()
```

View the schema:
```
changes.printSchema()
```

## Running on AWS

Requires the AWS [cli](https://aws.amazon.com/cli/).

### Generating change file

#### 1. Upload the JAR to s3
```
aws s3 cp target/scala-2.11/osm-history-analysis.jar s3://my-bucket/key
```

#### 2. Create the default EMR IAM roles if you don't already have them
```
aws emr create-default-roles
```

#### 3. Edit [steps.json](aws/steps.json)
Add:
- the JAR location on S3 from step 1, 
- the location of the input OSM history ORC on S3, and 
- your desired output location on S3.

#### 5. Spin up an EMR cluster:
```
aws emr create-cluster \
  --name "OSM History Analysis Cluster" \
  --region us-east-1 \
  --ec2-attributes SubnetId=subnet-######## \
  --instance-type m4.2xlarge
  --instance-count 5
  --use-default-roles \
  --configurations file://./aws/emrConfig.json \
  --visible-to-all-users \ 
  --applications Name="Spark" \
  --release-label emr-5.11.0 \
  --steps file://./aws/steps.json \
  --log-uri s3://log-bucket/prefix \
  --auto-terminate
```

Substitute one of your default VPC subnets in us-east-1 and your desired S3 url for logs.

This will start an application-specific EMR cluster -- i.e., the cluster will spin up, run the OSM history job, and then shut down. 

Using the EC2 types and cluster size specified above, a full world job should complete in about XXX hours.

### Querying in Athena

Coming...

## More on data output 

[Data Notes](data-notes.md) coming...

