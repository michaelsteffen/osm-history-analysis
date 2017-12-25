package com.michaelsteffen.osm.osmdata

import com.michaelsteffen.osm.rawosmdata._

object OSMDataUtils {
  def toOSMObjectHistory (id: String, rawObjHistory: Iterator[RawOSMObjectVersion]): OSMObjectHistory = OSMObjectHistory(
    id = id,
    objType = id(0).toString,
    versions = rawObjHistory
      .toList
      .sortWith(_.timestamp.getTime < _.timestamp.getTime)
      .map(toOSMObjectVersion)
  )

  def createID (id: Long, objType: String): String = objType match {
    case "node" => "n" + id.toString
    case "way" => "w" + id.toString
    case "relation" => "r" + id.toString
    case _ => "?" + id.toString
  }

  def hasGeometry (objType: String, objVersion: OSMObjectVersion): Boolean = {
    objVersion.isFeature && (objType match {
      case "n" | "w" => true
      case "r" => objVersion.featureTypeTags.getOrElse("type", "").equals("multipolygon")
    })
  }

  // TODO: define lat/lon for nodes/relations
  private def toOSMObjectVersion (obj: RawOSMObjectVersion): OSMObjectVersion = {
    val (featureTypeTags, featurePropertyTags) = splitTags(obj.`type`(0).toString, obj.tags)

    OSMObjectVersion(
      featureTypeTags = featureTypeTags,
      featurePropertyTags = featurePropertyTags,
      lat = obj.lat,
      lon = obj.lon,
      children = convertRefs(obj.nds, obj.members),
      parents = List.empty[String],
      majorVersion = obj.version,
      minorVersion = 0,
      changeset = obj.changeset,
      timestamp = obj.timestamp,
      visible = obj.visible
    )
  }

  private def splitTags (objType: String, tags: Map[String, Option[String]]): (Map[String, Option[String]], Map[String, Option[String]]) = {
    val featureTypeKeys = Set("aerialway", "aeroway", "amenity", "barrier", "boundary", "building",
      "craft", "emergency", "highway", "historic", "landuse", "leisure", "man_made", "military",
      "natural", "office", "place", "power", "public_transport", "railway", "route", "shop",
      "tourism", "waterway")

    var featureTypeTags = tags.filterKeys(k => featureTypeKeys.contains(k)
    if (objType == "r" && tags.contains("type")) featureTypeTags = featureTypeTags ++ Map("type" -> tags("type"))

    var featurePropertyTags = tags.filterKeys(k => !featureTypeKeys.contains(k))
    if (objType == "r") featurePropertyTags = featurePropertyTags - "type"

    (featureTypeTags, featurePropertyTags)
  }

  private def convertRefs (nodeRefs: List[RawNodeRef], memberRefs: List[RawMemberRef]): List[Ref] = {
    val newNodeRefs = nodeRefs.map(n => Ref(createID(n.ref, "node"), ""))
    val newMemberRefs = memberRefs.map(m => Ref(createID(m.ref, m.`type`), m.role))

    newNodeRefs ++ newMemberRefs
  }
}
