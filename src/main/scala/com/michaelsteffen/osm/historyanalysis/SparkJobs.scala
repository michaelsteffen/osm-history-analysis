package com.michaelsteffen.osm.historyanalysis

import com.michaelsteffen.osm.changes._
import com.michaelsteffen.osm.parentrefs._
import com.michaelsteffen.osm.osmdata._
import org.apache.spark.sql._
import org.apache.spark.storage.StorageLevel

object SparkJobs {
  def generateChanges(rawHistoryLocation: String, outputLocation: String, debugMode: Boolean): Unit = {
    val spark = SparkSession.builder.getOrCreate()
    import spark.implicits._
    val DEPTH = 10

    val refChanges = spark.read.orc(rawHistoryLocation).as[ObjectVersion]
      .filter(o => o.`type` == "way" || o.`type` == "relation" )
      //should be cheap shuffle b/c ORC should already be sorted this way?
      .groupByKey(obj => OSMDataUtils.createID(obj.id, obj.`type`))
      .flatMapGroups(RefUtils.generateRefChanges)

    val geometryStatuses = spark.read.orc(rawHistoryLocation)
      .as[ObjectVersion]
      .filter(o => o.`type` == "way" || o.`type` == "relation" )
      .map(ver => GeometryStatus(OSMDataUtils.createID(ver.id, ver.`type`), ver.timestamp, ver.hasGeometry))
      .groupByKey(_.id)

    val refTree = refChanges
      //expensive shuffle
      .groupByKey(_.childID)
      .cogroup(geometryStatuses)(RefUtils.generateRefTree)

    // setup for later iterative traversal of the tree . . .
    // on second pass we can look only at ways and relations, and all subsequent passes we can look only at relations
    val waysAndRelationsRefTree = refTree.filter(o => OSMDataUtils.isWay(o.id) || OSMDataUtils.isRelation(o.id))
    val relationsRefTree = refTree.filter(o => OSMDataUtils.isRelation(o.id))

    refTree.persist(StorageLevel.MEMORY_ONLY)
    relationsRefTree.persist(StorageLevel.MEMORY_ONLY)

    if (debugMode) refTree.show(100)

    // initialize accumulators
    val changesToSaveAndPropagate = new Array[Dataset[ChangeResults]](DEPTH)
    val changesToPropagate = new Array[Dataset[ChangeGroupToPropagate]](DEPTH)

    // iterate to propagate changes up through references up to 10 layers deep (e.g. relation->relation->relation->way->node)
    // this loop just creates the dependency graph, we don't knock down the dominoes just yet
    for (i <- 0 until DEPTH) {
      changesToSaveAndPropagate(i) =
        if (i == 0) spark.read.orc(rawHistoryLocation)
          .as[ObjectVersion]
          .groupByKey(obj => OSMDataUtils.createID(obj.id, obj.`type`))
          .mapGroups(ChangeUtils.generateFirstOrderChanges)
        else if (i == 1) refTree
          .joinWith(changesToPropagate(i-1), $"id" === $"parentID")
          .map(t => ChangeUtils.generateSecondOrderChanges(t._1, t._2, i-1, propagateOnly = true))
        else if (i == 2) waysAndRelationsRefTree
          .joinWith(changesToPropagate(i-1), $"id" === $"parentID")
          .map(t => ChangeUtils.generateSecondOrderChanges(t._1, t._2, i-1))
        else relationsRefTree
          .joinWith(changesToPropagate(i-1), $"id" === $"parentID")
          .map(t => ChangeUtils.generateSecondOrderChanges(t._1, t._2, i-1))

      if (debugMode && i == 0) changesToSaveAndPropagate(0).flatMap(_.changesToSave).show(100)

      changesToPropagate(i) = changesToSaveAndPropagate(i)
        .flatMap(_.changesToPropagate)
        .groupByKey(_.parentID)
        .mapGroups((id: Long, changes: Iterator[ChangeToPropagate]) => ChangeGroupToPropagate(id, changes.map(_.change).toArray))
    }

    // Almost ready . . .
    val changes =
      changesToSaveAndPropagate
      .map(_.flatMap(_.changesToSave))
      .reduceLeft(_.union(_))
      .groupByKey(_.featureID)
      .flatMapGroups((id, changes) => ChangeUtils.coalesceChanges(changes))

    // And . . . finally! Save all the changes, triggering our long chain of dominoes
    changes.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "changes.orc")
  }
}
