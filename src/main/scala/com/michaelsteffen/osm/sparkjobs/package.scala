package com.michaelsteffen.osm

import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.rawosmdata._
import com.michaelsteffen.osm.parentrefs._
import com.michaelsteffen.osm.changes._
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
      .mapGroups(RefUtils.collectRefChanges)

    val history = historyWithoutParentRefs
      .joinWith(refChangesGroupedByChild, $"id" === $"childID", "left_outer")
      .map(RefUtils.addParentRefs)

    history
  }

  def generateChanges(spark: SparkSession, history: Dataset[OSMObjectHistory]): Dataset[Change] = {
    import spark.implicits._

    var changesToSave = spark.emptyDataset[Change]
    var changesToPropagate = spark.emptyDataset[ChangeGroupToPropagate]
    var changesToPropagateCount: Long = 0
    var i = 0
    do {
      i = i + 1
      val changesToSaveAndPropagate =
        if (changesToPropagateCount == 0) history.map(ChangeUtils.generateFirstOrderChanges) //first loop only
        else history
          .joinWith(changesToPropagate, $"id" === $"parentID")
          .map(t => ChangeUtils.generateSecondOrderChanges(t._1, t._2))

      changesToSave = changesToSaveAndPropagate
        .flatMap(_.changesToSave)
        .union(changesToSave)
      changesToPropagate = changesToSaveAndPropagate
        .flatMap(_.changesToPropagate)
        .groupByKey(_.parentID)
        .mapGroups(ChangeUtils.collectChangesToPropagate)
      changesToPropagateCount = changesToPropagate.count
      println(s"Iteration $i complete. Changes to propagate: $changesToPropagateCount.")
    } while (changesToPropagateCount > 0)

    changesToSave
  }
}
