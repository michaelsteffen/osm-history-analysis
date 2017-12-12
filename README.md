Generate easily queryable OpenStreetMap change history data using Apache Spark.

Starts with OpenStreetMap ORC file generated from [osm2orc](https://github.com/mojodna/osm2orc)

This repo includes ORC-formatted data for Washington, DC to get you started. When you are ready to go global, you can use the world history file [hosted by AWS](https://aws.amazon.com/public-datasets/osm/)

# Running Locally

1. Install Spark 2.2.0
2. Start spark shell:
  - `spark-shell --jars target/scala-2.11/osm-history-analysis.jar`
3. Generate history for DC:
  - `scala> val history = generateHistory(spark, "data/district-of-columbia.osh.orc")`
4. Query the data. For example:
  - Count of objects in the history:
    - `scala> history.count`
  - Count of current objects:
    - `scala> history.filter(_.versions.last.visible).count`
  - Count of current ways:
    - `scala> history.filter((obj) => obj.objType == "w" && obj.versions.last.visible).count`

# Running on AWS

# Data Format

See the [osmdata](src/main/scala/com/michaelsteffen/osm/osmdata) package for the full list of available fields.