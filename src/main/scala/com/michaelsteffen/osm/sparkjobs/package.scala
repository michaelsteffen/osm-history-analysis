package com.michaelsteffen.osm

import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.rawosmdata._
import com.michaelsteffen.osm.parentrefs._
import org.apache.spark.sql._

package object sparkjobs {
  def generateHistory(spark: SparkSession, orcFile: String): Dataset[OSMObjectHistory] = {
    import spark.implicits._
    val rawHistory = spark.read.orc(orcFile).as[RawOSMObjectVersion]

    val historyWithoutParentRefs = rawHistory
      .groupByKey(obj => OSMDataUtils.createID(obj.id, obj.`type`))
      .mapGroups(OSMDataUtils.toOSMObjectHistory)

    val refChangesGroupedByChild = historyWithoutParentRefs
      .flatMap(RefUtils.generateRefChangesFromObjectHistory)
      .groupByKey(_.childID)
      .mapGroups(RefUtils.consolidateRefChanges)

    val history = historyWithoutParentRefs
      .joinWith(refChangesGroupedByChild, $"id" === $"childID", "left_outer")
      .map(RefUtils.addParentRefs)

    history
  }
}
