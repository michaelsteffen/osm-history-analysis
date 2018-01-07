package com.michaelsteffen.osm.historyanalysis

import com.michaelsteffen.osm.changes._
import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.parentrefs._
import com.michaelsteffen.osm.rawosmdata._
import org.apache.spark.sql._
import org.apache.spark.storage.StorageLevel

object SparkJobs {
  def generateRefTree(rawHistoryLocation: String, outputLocation: String): Unit = {
    val spark = SparkSession.builder.getOrCreate()
    import spark.implicits._
    val DEPTH = 10

    val RefChanges = spark.read.orc(rawHistoryLocation)
      .as[RawOSMObjectVersion]
      .filter(o => o.`type` == "way" || o.`type` == "rel" )
      .groupByKey(obj => OSMDataUtils.createID(obj.`type`, obj.id) //cheap shuffle b/c ORC is already sorted this way
      .flatMapGroups(RefUtils.generateReferenceChangesFromWayAndRelationHistories)

    // RefChanges.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "RefChanges.orc")

    val RefTree = RefChanges
      .groupByKey(_.childID)                                       //expensive shuffle
      .mapGroups(RefUtils.coaleseRefTreeFromRefChanges)

    // on first pass we can look only at ways and relations, and all subsequent passes we can look only at relations
    val fullRefTree = RefTree
    val waysAndRelationsRefTree = fullRefTree.filter(o => o.isWay || o.isRelation)
    val relationsRefTree = fullRefTree.filter(o => o.isRelation)
    fullRefTree.persist(StorageLevel.MEMORY_ONLY)

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
    changesToPropagate(9).persist(StorageLevel.MEMORY_ONLY)

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
