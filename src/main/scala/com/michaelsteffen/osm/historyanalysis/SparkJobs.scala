package com.michaelsteffen.osm.historyanalysis

import com.michaelsteffen.osm.changes._
import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.parentrefs._
import com.michaelsteffen.osm.rawosmdata._
import org.apache.spark.sql._
import org.apache.spark.storage.StorageLevel

object SparkJobs {
  def generateChanges(rawHistoryLocation: String, outputLocation: String): Unit = {
    val spark = SparkSession.builder.getOrCreate()
    import spark.implicits._
    val DEPTH = 10

    val RefChanges = spark.read.orc(rawHistoryLocation)
      .as[RawOSMObjectVersion]
      .filter(o => o.`type` == "way" || o.`type` == "rel" )
      .groupByKey(obj => OSMDataUtils.createID(obj.`type`, obj.id) //cheap shuffle b/c ORC is already sorted this way
      .flatMapGroups(RefUtils.generateRefChanges)

    // RefChanges.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "RefChanges.orc")

    val fullRefTree = RefChanges
      .groupByKey(_.childID)                                       //expensive shuffle
      .sortwithingroupsby(timestamp)
      .mapGroups(RefUtils.coaleseRefTree)

    // setup for iterative traversal of the tree . . .
    // on second pass we can look only at ways and relations, and all subsequent passes we can look only at relations
    val waysAndRelationsRefTree = fullRefTree.filter(o => o.isWay || o.isRelation)
    val relationsRefTree = fullRefTree.filter(o => o.isRelation)
    fullRefTree.persist(StorageLevel.MEMORY_ONLY)
    // ??? on this one
    relationsRefTree.persist(StorageLevel.MEMORY_ONLY)

    // initialize accumulators
    val changesToSaveAndPropagate = new Array[Dataset[ChangeResults]](DEPTH)
    val changesToPropagate = new Array[Dataset[ChangeGroupToPropagate]](DEPTH)

    // iterate to propagate changes up through references up to 10 layers deep (e.g. relation->relation->relation->way->node)
    // remember -- this loop just creates the dependency graph, we don't knock down the dominoes just yet
    for (i <- 0 until DEPTH) {
      changesToSaveAndPropagate(i) =
        if (i == 0)
          spark.read.orc(rawHistoryLocation)
            .as[RawOSMObjectVersion]
            .map(ChangeUtils.generateFirstOrderChanges)
        else if (i == 1)
          waysAndRelationsRefTree
          .joinWith(changesToPropagate(i-1), $"id" === $"parentID")
          .map(t => ChangeUtils.generateSecondOrderChanges(t._1, t._2))
        else
          relationsRefTree
            .joinWith(changesToPropagate(i-1), $"id" === $"parentID")
            .map(t => ChangeUtils.generateSecondOrderChanges(t._1, t._2))

      changesToPropagate(i) = changesToSaveAndPropagate(i)
        .flatMap(_.changesToPropagate)
        .groupByKey(_.parentID)
        .mapGroups(ChangeUtils.collectChangesToPropagate)
    }

    // flag to hold onto this one so we don't have to generate it twice. should be empty or close to it
    changesToPropagate(9).persist(StorageLevel.MEMORY_ONLY)

    // Almost ready . . .
    val changes =
      changesToSaveAndPropagate
      .map(_.flatMap(_.changesToSave))
      .reduceLeft(_.union(_))
      .groupByKey(_.featureID)
      .flatMapGroups((id, changes) => ChangeUtils.coalesceChanges(changes))

    // And . . . BOOM! Save all the changes, the first action, finally triggering our long chain of dominoes
    changes.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "changes.orc")

    // save any residual changesToPropagate so we can see what (if any) failed to resolve after 10 iterations
    changesToPropagate(9).write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "residualChangesToPropagate.orc")
  }
}
