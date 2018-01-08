package com.michaelsteffen.osm.historyanalysis

import com.michaelsteffen.osm.changes._
import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.parentrefs._
import com.michaelsteffen.osm.rawosmdata._
import org.apache.spark.sql._
import org.apache.spark.storage.StorageLevel

object SparkJobs {
  def generateHistory(rawHistoryLocation: String, outputLocation: String, saveIntermediateDatasets: Boolean): Unit = {
    val spark = SparkSession.builder.getOrCreate()
    import spark.implicits._

    val rawHistory = spark.read.orc(rawHistoryLocation).as[RawOSMObjectVersion]

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

    history.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "history.orc")
    historyWithoutParentRefs.unpersist()
  }

  def generateChanges(historyLocation: String, outputLocation: String, saveIntermediateDatasets: Boolean): Unit = {
    val spark = SparkSession.builder.getOrCreate()
    import spark.implicits._
    val DEPTH = 10

    // on first pass we can look only at ways and relations, and all subsequent passes we can look only at relations
    val fullHistory = spark.read.orc(historyLocation).as[OSMObjectHistory]
    val waysAndRelations = fullHistory.filter(o => o.objType == "w" || o.objType == "r" )
    val relations = fullHistory.filter(o => o.objType == "r" )
    relations.persist(StorageLevel.MEMORY_ONLY_SER)

    // initialize accumulators
    val changesToSaveAndPropagate = new Array[Dataset[ChangeResults]](DEPTH)
    val changesToPropagate = new Array[Dataset[ChangeGroupToPropagate]](DEPTH)

    // iterate to propagate node moves through references up to 10 layers deep (e.g. relation->relation->relation->way->node)
    // this loop just creates the dependency graph, we don't knock down the dominoes just yet
    for (i <- 0 until DEPTH) {
      changesToSaveAndPropagate(i) =
        if (i == 0)
          fullHistory.map(ChangeUtils.generateFirstOrderChanges) //first loop only
        else if (i == 1)
          waysAndRelations
          .joinWith(changesToPropagate(i-1), $"id" === $"parentID")
          .map(t => ChangeUtils.generateSecondOrderChanges(t._1, t._2))
        else
          relations
            .joinWith(changesToPropagate(i-1), $"id" === $"parentID")
            .map(t => ChangeUtils.generateSecondOrderChanges(t._1, t._2))

      changesToPropagate(i) = changesToSaveAndPropagate(i)
        .flatMap(_.changesToPropagate)
        .groupByKey(_.parentID)
        .mapGroups(ChangeUtils.collectChangesToPropagate)
    }

    // changesToSaveAndPropagate appears twice in the dependency graph. to avoid recomputing, we setup caching on each iteration
    // in trial runs, this persist made only a _very_ small difference (1%) vs recompute, but it makes the DAG much cleaner so what the heck
    // changesToSaveAndPropagate.foreach(_.persist(StorageLevel.MEMORY_AND_DISK_SER))
    changesToSaveAndPropagate(9).persist(StorageLevel.MEMORY_ONLY)

    // BOOM! Get all the changes to save, knocking down our 10 dominoes
    val changes =
      changesToSaveAndPropagate
      .map(_.flatMap(_.changesToSave))
      .reduceLeft(_.union(_))
      .groupByKey(_.featureID)
      .flatMapGroups((id, changes) => ChangeUtils.coalesceChanges(changes))

    changes.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "changes.orc")

    // save any residual changesToPropagate so we can see what (if any) failed to resolve after 10 iterations
    changesToPropagate(9).write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "residualChangesToPropagate.orc")
  }
}
