package com.michaelsteffen.osm.historyaugmenter

import com.michaelsteffen.osm.changes._
import com.michaelsteffen.osm.geometries.{ChangeGroupToPropagate, ChangeResults, ChangeToPropagate}
import com.michaelsteffen.osm.parentrefs._
import com.michaelsteffen.osm.osmdata._
import org.apache.spark.sql._
import org.apache.spark.storage.StorageLevel

object SparkJobs {
  def generateGeometryChanges(rawHistoryLocation: String, outputLocation: String): Unit = {
    val spark = SparkSession.builder.getOrCreate()
    import spark.implicits._
    val DEPTH = 10

    // 1. Create a ref tree from children -> parents

    val refChanges = spark.read.orc(rawHistoryLocation)
      .as[ObjectVersion]
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

    // 2. Create feature geometries (using the ref tree)

    // setup for iterative traversal of the ref tree: on second pass we can look only at ways and relations, and all
    // subsequent passes we can look only at relations
    val waysAndRelationsRefTree = refTree.filter(o => OSMDataUtils.isWay(o.id) || OSMDataUtils.isRelation(o.id))
    val relationsRefTree = refTree.filter(o => OSMDataUtils.isRelation(o.id))

    refTree.persist(StorageLevel.MEMORY_ONLY)
    relationsRefTree.persist(StorageLevel.MEMORY_ONLY)

    // gather node locations at all key times: (a) when nodes are first added to a feature, and (b) when the location changes
    val nodeLocationsToPropagate = spark.read.orc(rawHistoryLocation)
      .as[ObjectVersion]
      .filter(o => o.`type` == "node")
      .groupByKey(obj => OSMDataUtils.createID(obj.id, obj.`type`))
      .map((id, vers) => ObjectHistory(id, vers))
      .joinWith(refTree)
      .map(GeometryUtils.generateKeyNodeLocationVersions)

    // initialize accumulators
    val geometriesToSaveAndPropagate = new Array[Dataset[ChangeResults]](DEPTH)
    val geometriesToPropagate = new Array[Dataset[ChangeGroupToPropagate]](DEPTH)

    // propagate geometries up through references up to 10 layers deep (e.g. relation->relation->relation->way->node)
    for (i <- 0 until DEPTH) {
      geometriesToSaveAndPropagate =
        if (i == 0) waysAndRelationsRefTree
          .joinWith(nodeLocationsToPropagate, $"id" === $"parentID")
          .map(t => GeometryUtils.generateGeometries(t._1, t._2, i))
        else relationsRefTree
          .joinWith(geometriesToPropagate(i-1), $"id" === $"parentID")
          .map(t => GeometryUtils.generateGeometries(t._1, t._2, i-1))

      geometriesToPropagate(i) = geometriesToSaveAndPropagate(i)
        .flatMap(_.geometriesToPropagate)
        .groupByKey(_.parentID)
        .mapGroups((id: Long, changes: Iterator[ChangeToPropagate]) => ChangeGroupToPropagate(id, changes.map(_.change).toArray))
    }

    val geometries = geometriesToSaveAndPropagate
      .map(_.flatMap(_.geometriesToSave))
      .reduceLeft(_.union(_))
      .groupByKey(_.featureID)
      .flatMapGroups((id, geometries) => GeometryUtils.combineGeometries(geometries))

    // 3. Combine raw OSM data with geometries and ref tree to create full augmented history

    val augmentedHistory = spark.read.orc(rawHistoryLocation).as[ObjectVersion]
      .join(refTree)
      .map(historyUtils.addRefs)
      .join(geomtries)
      .map(historyUtils.addGeometries)

    // Finally, save the results
    augmentedHistory.write.mode(SaveMode.Overwrite).format("orc").save(outputLocation + "augmented-history.orc")
  }
}
