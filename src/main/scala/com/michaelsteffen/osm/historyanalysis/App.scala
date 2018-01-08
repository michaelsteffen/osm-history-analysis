package com.michaelsteffen.osm.historyanalysis

import org.apache.spark.sql._
import com.michaelsteffen.osm.rawosmdata._

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

        // set checkpoint location
        // spark.sparkContext.setCheckpointDir(opts("outputDataLocation") + "checkpoints")

        // set parallelism based on input data size and cluster size
        val recordCount = spark.read.orc(opts("inputDataLocation")).as[RawOSMObjectVersion].count()
        optimizeParallelism(recordCount)

        // do all the things
        SparkJobs.generateChanges(opts("outputDataLocation") + "history.orc", opts("outputDataLocation"))

        spark.stop()
    }
  }

  private def parseArgs(args: Array[String]): Option[Map[String, String]] = {
    if (args.length != 2) return None

    val inputDataLocation = args(0)
    val outputDataLocation = args(1)

    //TODO: check validity of inputs
    //TODO: add trailing slash to output data location if it doesn't have it

    Some(Map(
      "inputDataLocation" -> inputDataLocation,
      "outputDataLocation" -> outputDataLocation
    ))
  }

  private def optimizeParallelism(numRecords: Long): Unit = {
    // based on some experimentation with samples:
    //   - data size is generally ~100 bytes per OSM object
    //   - we want shuffle blocks on average about 50MB to avoid out of memory errors
    // so we set partitions for the whole operation chain to:
    //   num objects * 50MB / 100 bytes  = numObjects / 500,000
    // we also want to fully utilize all cores, so we set a floor of:
    //   number of cores in the cluster * 4

    val numPartitions = numRecords/500000
    val spark = SparkSession.builder.getOrCreate()
    val numExecutors = spark.conf.get("spark.executor.instances").toInt
    val coresPerExecutor = spark.conf.get("spark.executor.cores").toInt

    val partitions = Math.max(numPartitions, numExecutors*coresPerExecutor*4)
    spark.conf.set("spark.sql.shuffle.partitions", partitions)
    println(s"Read $numRecords objects. Found $numExecutors executors and $coresPerExecutor cores/executor. Set parallelism to $partitions.")
  }
}