[Work in Progress]

Generate easily queryable OpenStreetMap change histories using Spark. Process the entire global history of OpenStreetMap -- [[10 billion edits]] -- in 6 hours for under $20, on AWS ElasticMapReduce.

Uses OpenStreetMap ORC files generated with [osm2orc](https://github.com/mojodna/osm2orc). This repo includes ORC-formatted data for Washington, DC to help you test your build locally. When you're ready to go global, you can use the planet history file [hosted by AWS](https://aws.amazon.com/public-datasets/osm/). 

## Building

Requires [sbt](https://www.scala-sbt.org/)

```
sbt assembly
```

## Running and querying locally 

Requires Spark 2.2.0+

### Generating change data
```
spark-submit \
 --class com.michaelsteffen.osm.historyanalysis.App \
 --master local[*] \
 --driver-memory 50g \
 target/scala-2.11/osm-history-analysis.jar \
 data/district-of-columbia.osh.orc \
 path/to/output/
```

### Querying in spark-shell

#### Start spark-shell
```
spark-shell --jars target/scala-2.11/osm-history-analysis.jar
```

#### Import some things and load the data
```
import org.apache.spark.sql.functions._
import com.michaelsteffen.osm.changes._

val changes = spark.read.orc("path/to/changes.orc").as[Change]
```

#### View the schema
```
changes.printSchema()
```

#### Example: Count of new features by year
```
changes
  .filter($"changeType" === ChangeUtils.FEATURE_CREATE)
  .groupBy(year($"timestamp"))
  .count
  .sort(desc("year(timestamp)"))
  .show()
```

## Running on AWS ElasticMapReduce and querying in AWS Athena

Now for the fun part. Requires the AWS [cli](https://aws.amazon.com/cli/).

### Generating change files

_This script doesn't exist yet_

```
./emr-run.sh
```

The script will prompt you for:
- a VPC subnet to run your cluster in (a subnet in us-east-1 recommended if you are running on the AWS-provided planet)
- the location of the input OSM history ORC file(s) on S3 (use s3://osm-pds/planet-history/ for the AWS-provided planet)
- an AWS bucket to be used for (1) the built JAR, (2) logs, and (3) outputs
- a prefix within the bucket (optional)
- cluster size (sm/lg/xl) (explained below)

This will start an application-specific EMR cluster -- i.e., the cluster will spin up, run the OSM history job, and then shut down. 

// TODO: Make shell script do everything below here:

Create the default EMR IAM roles if you don't already have them
```
aws emr create-default-roles
```

-Upload the JAR to s3
```
aws s3 cp target/scala-2.11/osm-history-analysis.jar s3://my-bucket/my-prefix/osm-history-analysis.jar
```

-Spin up an EMR cluster
```
aws emr create-cluster \
  --name "OSM History Analysis Cluster" \
  --region us-east-1 \
  --ec2-attributes SubnetId=subnet-######## \
  --instance-groups file://./aws/instanceGroups-sm.json \
  --configurations file://./aws/emrConfig-sm.json \
  --use-default-roles \
  --visible-to-all-users \ 
  --applications Name="Spark" \
  --release-label emr-5.11.0 \
  --steps file://./aws/steps.json \
  --log-uri s3://log-bucket/prefix \
  --auto-terminate
```

### Querying in Athena

In the Athena console, or via the AWS CLI...

#### Create the tables
```
CREATE EXTERNAL TABLE changes (
  featureID BIGINT,
  changeType INT,
  count INT,
  version BIGINT,
  tags MAP<STRING, STRING>,
  tagChanges MAP<STRING, STRING>,,
  bbox STRUCT<min: STRUCT<lon: DECIMAL(10,7), lat: DECIMAL(9,7)>, max: STRUCT<lon: DECIMAL(10,7),lat: DECIMAL(9,7)>>,
  timestamp TIMESTAMP, 
  changeset BIGINT
  depth INT
)
STORED AS ORCFILE
LOCATION 's3://bucket/prefix/changes.orc';
```

#### Example: Count by change type in 2017
```
WITH changeTypes AS (
  SELECT * FROM (
    VALUES
      (0, 'FEATURE_CREATE'),
      (1, 'FEATURE_DELETE'),
      (2, 'TAG_ADD'),
      (3, 'TAG_DELETE'),
      (4, 'TAG_MODIFY'),
      (5, 'NODE_MOVE'),
      (6, 'NODE_ADD'),
      (7, 'NODE_REMOVE'),
      (8, 'MEMBER_ADD'),
      (9, 'MEMBER_REMOVE')
  ) AS t (type, name) 
)

SELECT changeTypes.name, count(*) AS changes
FROM changes LEFT JOIN changeTypes ON changes.changeType = changeTypes.type
WHERE year(changes.timestamp) = 2017
GROUP BY changeTypes.name
ORDER BY count(*) DESC
```

### Notes on AWS EMR configurations

I've included configurations for 3 different recommended cluster sizes in [./aws](/aws). All configurations run on Spot instances, with a bid at the On Demand price. I've had no issues with Spot interruptions, but if you're worried about it you can switch to Defined Duration or On Demand instances.

- Small -- Able to process an ORC of ~1.5 GB (e.g., large US state or European country) in about 2 hours. Approx. $0.20-$0.40/hr depending on Spot prices.
- Large -- 20x compute, 55x memory, 50x disk vs small. Able to process the planet history in about 4.5 hours. Approx. [[$3.20-$6.40/hr]] depending on Spot prices.

Processing a 1.5 MB ORC -- about 1/40 the size of the planet -- on the small cluster is a good way to test changes to the codebase. Indeed, the small cluster is optimized for testing, not for production processing of excerpts. If a 1.5GB excerpt runs on the small cluster, you can be reasonably confident that a planet job will run on the large cluster. (The one exception is out-of-memory errors on the driver at planet-scale, which the small cluster will not help you catch.) California works well for this kind of testing.

If you want to tweak the cluster configurations, you'll need to edit both the instanceGroup and emrConfig files in the /aws directory. The memory allocation is the trickiest part. See [my notes](aws/README.md). 

## More on data output 

Coming...
