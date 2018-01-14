package com.michaelsteffen.osm.historyanalysis

import org.apache.spark.sql._
import com.michaelsteffen.osm.osmdata._

object App {
  def main(args: Array[String]): Unit = {
    val usage = """
      Usage: See README.md
    """

    parseArgs(args) match {
      case None => println(usage)
      case Some(opts) =>
        val spark = SparkSession.builder
          .appName("OpenStreetMap History Analysis")
          .getOrCreate()
        import spark.implicits._

        // reduce Spark's logging in debug mode so we can actually see the things we're interested in
        if (opts.debugMode) {
          println("Setting log level to WARN.")
          import org.apache.log4j.Logger
          import org.apache.log4j.Level
          Logger.getLogger("org").setLevel(Level.WARN)
          Logger.getLogger("akka").setLevel(Level.WARN)
        }

        // set parallelism based on input data size and cluster size
        val recordCount = spark.read.orc(opts.inputDataLocation).as[ObjectVersion].count()
        optimizeParallelism(recordCount)

        // do all the things
        SparkJobs.generateChanges(opts.inputDataLocation, opts.outputDataLocation, opts.debugMode)

        spark.stop()
    }
  }

  private def parseArgs(args: Array[String]): Option[Opts] = {
    if (args.length < 2 || args.length > 3) return None
    //TODO: check validity of inputs
    //TODO: add trailing slash to output data location if it doesn't have it

    Some(Opts(
      inputDataLocation = args(0),
      outputDataLocation = args(1),
      debugMode = if (args.length == 3 && args(2) == "--debug") true else false
    ))
  }

  private def optimizeParallelism(numRecords: Long): Unit = {
    // based on some experimentation with samples:
    //   - data size is generally ~100 bytes per OSM object
    //   - we aim for shuffle blocks on average about 125MB
    //     (see https://www.slideshare.net/cloudera/top-5-mistakes-to-avoid-when-writing-apache-spark-applications)
    // so we set partitions for the whole operation chain to:
    //   num objects * 100 bytes / 1250MB  = numObjects / 1,250,000
    // we also want to fully utilize all cores, so we set a floor of:
    //   number of cores in the cluster * 4

    val numPartitions = numRecords/500000
    val spark = SparkSession.builder.getOrCreate()
    val numExecutors = spark.conf.getOption("spark.executor.instances").getOrElse("1").toInt
    val coresPerExecutor = spark.conf.getOption("spark.executor.cores").getOrElse("2").toInt

    val partitions = Math.max(numPartitions, numExecutors*coresPerExecutor*4)
    spark.conf.set("spark.sql.shuffle.partitions", partitions)
    spark.conf.set("spark.default.parallelism", partitions)
    println(s"Read $numRecords objects. Found $numExecutors executors and $coresPerExecutor cores/executor. Set parallelism to $partitions.")
  }
}