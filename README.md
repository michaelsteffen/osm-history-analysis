Generate easily queryable OpenStreetMap change history using Spark. Uses OpenStreetMap ORC file generated from [osm2orc](https://github.com/mojodna/osm2orc).

This repo includes ORC-formatted data for Washington, DC to get you started. When you are ready to go global, you can use the world history file [hosted by AWS](https://aws.amazon.com/public-datasets/osm/).

## Building

Requires [sbt](https://www.scala-sbt.org/)

```
sbt assembly
```

## Running Locally

#### 1. Install Spark 2.2.0

#### 2. Start spark shell with plenty of memory:
```
spark-shell --driver-memory 5g --jars target/scala-2.11/osm-history-analysis.jar
```

#### 3. Generate augmented history for DC:
```
import com.michaelsteffen.osm.sparkjobs._

val history = generateHistory(spark, "district-of-columbia.osh.orc")
```

#### 4. Generate changes for DC:
```
val changes = generateChanges(spark, history)
```

#### 5. Query the data. 

Import SQL functions:
```
import org.apache.spark.sql.functions._
```

Count of new primary features by year:
```
changes
  .filter($"changeType" === 0)
  .groupBy(year($"timestamp"))
  .count
  .sort(desc("year(timestamp)"))
  .show()
```

Count of new buildings in 2017:
```
changes
  .filter(_.primaryFeatureTypes == List("building"))
  .filter(year($"timestamp") === 2017)
  .filter($"changeType" === 0)
  .count
```

All changes for a specific feature, in order:
```
changes
  .filter($"primaryFeatureID" === "w226013371")
  .sort($"timestamp")
  .show()
```

See the [osmdata](src/main/scala/com/michaelsteffen/osm/osmdata) package for the full list of available fields.

## Running on AWS

[Coming...]

## Notes on the data format

[Coming...]

