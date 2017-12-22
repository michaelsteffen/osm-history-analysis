package com.michaelsteffen.osm.historyanalysis

import com.michaelsteffen.osm.rawosmdata._
import org.apache.spark.sql._

object App {
  def main(args: Array[String]): Unit = {
    val usage = """
      Usage: See README.md
    """

    parseArgs(args) match {
      case None => println(usage)
      case Some(opts) => {
        val spark = SparkSession.builder.appName("OpenStreetMap History Analysis").getOrCreate()
        import spark.implicits._

        /* val rawHistory = opts("inputDataSource") match {
          case "path" => spark.read.orc(opts("inputDataLocation")).as[RawOSMObjectVersion]
          case "s3" => throw new Exception("s3 not yet supported") // TODO (P2)
        } */

        val rawHistory = spark.read.orc(opts("inputDataLocation")).as[RawOSMObjectVersion]

        val history = SparkJobs.generateHistory(spark, rawHistory)
        val changes = SparkJobs.generateChanges(spark, history)

        changes.write.format("orc").save(opts("outputDataLocation"))

        /* opts("outputDataSource") match {
          case "path" => changes.write.format("orc").save(opts("outputDataLocation")) // TODO (P1)
          case "s3" => throw new Exception("s3 not yet supported") // TODO (P2)
        } */

        spark.stop()
      }
    }
  }

  private def parseArgs(args: Array[String]): Option[Map[String, String]] = {
    if (args.length != 2) return None

    val (inputDataSource, inputDataLocation) = uriParse(args(0))
    val (outputDataSource, outputDataLocation) = uriParse(args(1))

    Some(Map(
      "inputDataSource" -> inputDataSource, "inputDataLocation" -> inputDataLocation,
      "outputDataSource" -> outputDataSource, "outputDataLocation" -> outputDataLocation
    ))
  }

  private def uriParse(uri: String): (String, String) = uri match {
    case "s3://" => ("s3", "") // TODO (P2)
    case s => ("path", s)
  }
}