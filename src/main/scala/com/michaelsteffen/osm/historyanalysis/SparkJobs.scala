package com.michaelsteffen.osm.historyanalysis

import com.michaelsteffen.osm.changes._
import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.parentrefs._
import com.michaelsteffen.osm.rawosmdata._
import org.apache.spark.sql._

object SparkJobs {
  def generateHistory(spark: SparkSession, rawHistory: Dataset[RawOSMObjectVersion]): Dataset[OSMObjectHistory] = {
    import spark.implicits._

    val historyWithoutParentRefs = rawHistory
      .groupByKey(obj => OSMDataUtils.createID(obj.id, obj.`type`))
      .mapGroups(OSMDataUtils.toOSMObjectHistory)

    val refChangesGroupedByChild = historyWithoutParentRefs
      .flatMap(RefUtils.generateRefChangesFromObjectHistory)
      .groupByKey(_.childID)
      .mapGroups(RefUtils.collectRefChangesForChild)

    val history = historyWithoutParentRefs
      .joinWith(refChangesGroupedByChild, $"id" === $"childID", "left_outer")
      .map(t => RefUtils.addParentRefs(t._1, t._2))

    history
  }

  def generateChanges(spark: SparkSession, history: Dataset[OSMObjectHistory]): Dataset[Change] = {
    import spark.implicits._

    var changesToSave = spark.emptyDataset[Change]
    var changesToPropagate = spark.emptyDataset[ChangeGroupToPropagate]

    // we check for references up to 10 layers deep (e.g. relation->relation->relation->way->node)
    // TODO: Add output for how many references don't bottom out at this level
    for (i <- 0 to 9) {
      val changesToSaveAndPropagate =
        if (i == 0) history.map(ChangeUtils.generateFirstOrderChanges) //first loop only
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
    }

    changesToSave
      .groupByKey(_.featureID)
      .flatMapGroups((id, changes) => ChangeUtils.coalesceChanges(changes))
  }
}
