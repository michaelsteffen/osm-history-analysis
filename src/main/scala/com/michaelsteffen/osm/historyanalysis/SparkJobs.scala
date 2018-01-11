package com.michaelsteffen.osm.historyanalysis

import com.michaelsteffen.osm.changes._
import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.parentrefs._
import com.michaelsteffen.osm.osmdata._
import org.apache.spark.sql._
import org.apache.spark.storage.StorageLevel

object SparkJobs {
  def generateChanges(rawHistoryLocation: String, outputLocation: String): Unit = {
    val spark = SparkSession.builder.getOrCreate()
    import spark.implicits._
    val DEPTH = 10

    val RefChanges = spark.read.orc(rawHistoryLocation)
      .as[ObjectVersion]
      .filter(o => o.`type` == "way" || o.`type` == "rel" )
      //should be cheap shuffle b/c ORC should already be sorted this way?
      .groupByKey(obj => OSMDataUtils.createID(obj.id, obj.`type`))
      .flatMapGroups(RefUtils.generateRefChanges)

    val fullRefTree = RefChanges
      //expensive shuffle
      .groupByKey(_.childID)
      .mapGroups(RefUtils.generateRefHistory)
      .repartition($"id")

    // fullRefTree.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "RefTree.orc")

    // setup for later iterative traversal of the tree . . .
    // on second pass we can look only at ways and relations, and all subsequent passes we can look only at relations
    // val waysAndRelationsRefTree = fullRefTree.filter(o => OSMDataUtils.isWay(o.id) || OSMDataUtils.isRelation(o.id))
    // val relationsRefTree = fullRefTree.filter(o => OSMDataUtils.isRelation(o.id))
    fullRefTree.persist(StorageLevel.MEMORY_ONLY)

    // this GroupBy doesn't do anything since id is unique, but gets us a KeyValueGeoupedDataset we can use below
    // it should be fast since the repartition above tells spark that we're already partitioned on id
    val keyValueRefTree = fullRefTree.groupByKey(_.id)

    // ??? on this one
    // relationsRefTree.persist(StorageLevel.MEMORY_ONLY)

    // initialize accumulators
    val changesToSaveAndPropagate = new Array[Dataset[ChangeResults]](DEPTH)
    val changesToPropagate = new Array[Dataset[ChangeToPropagate]](DEPTH)
    val groupedChangesToPropagate = new Array[KeyValueGroupedDataset[Long, ChangeToPropagate]](DEPTH)

    // iterate to propagate changes up through references up to 10 layers deep (e.g. relation->relation->relation->way->node)
    // this loop just creates the dependency graph, we don't knock down the dominoes just yet
    // -- Refactoring done through here --
    for (i <- 0 until DEPTH) {
      changesToSaveAndPropagate(i) =
        if (i == 0)
          spark.read.orc(rawHistoryLocation)
            .as[ObjectVersion]
            .groupByKey(obj => OSMDataUtils.createID(obj.id, obj.`type`))
            .cogroup(keyValueRefTree, ChangeUtils.generateFirstOrderChanges)
        else
          keyValueRefTree.cogroup(groupedChangesToPropagate(i-1), ChangeUtils.generateSecondOrderChanges)

      changesToPropagate(i) = changesToSaveAndPropagate(i).flatMap(_.changesToPropagate)
      groupedChangesToPropagate(i) = changesToPropagate(i).groupByKey(_.parentID)
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

    // And . . . finally! Save all the changes, triggering our long chain of dominoes
    changes.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "changes.orc")

    // save any residual changesToPropagate so we can see what (if any) failed to resolve after 10 iterations
    changesToPropagate(9).write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "residualChangesToPropagate.orc")
  }
}
