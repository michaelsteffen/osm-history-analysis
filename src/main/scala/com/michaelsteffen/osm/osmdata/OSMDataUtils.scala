package com.michaelsteffen.osm.osmdata

import com.michaelsteffen.osm.rawosmdata._

object OSMDataUtils {
  val PrimaryFeatures = Set("aerialway", "aeroway", "amenity", "barrier", "boundary", "building",
    "craft", "emergency", "highway", "historic", "landuse", "leisure", "man_made", "military",
    "natural", "office", "place", "power", "public_transport", "railway", "route", "shop",
    "tourism", "waterway")

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

  // TODO: define lat/lon for nodes/relations
  private def toOSMObjectVersion (obj: RawOSMObjectVersion): OSMObjectVersion = OSMObjectVersion(
    primaryFeatureTypes = getPrimaryFeatureType(obj.`type`(0).toString, obj.tags),
    tags = obj.tags,
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

  private def getPrimaryFeatureType (objType: String, tags: Map[String, Option[String]]): List[String] = {
    val namedPrimaryFeatureTypes = tags.keys.filter((key) => PrimaryFeatures.contains(key)).toList

    if (namedPrimaryFeatureTypes.nonEmpty) {
      namedPrimaryFeatureTypes
    } else {
      objType match {
        case "n" | "w" => if (tags.nonEmpty) List("unknown") else List.empty[String]
        case "r" =>
          // this makes old style multipolygons (i.e. ones w/ no tags on the relation) non-primary features, but that's
          // OK, since their outer way(s) will be primary features
          if (tags.get("type").contains(Some("multipolygon"))) {
            if (tags.size > 1) List("unknown") else List.empty[String]
          } else {
            if (tags.nonEmpty) List("unknown") else List.empty[String]
          }
      }
    }
  }

  private def convertRefs (nodeRefs: List[RawNodeRef], memberRefs: List[RawMemberRef]): List[Ref] = {
    val newNodeRefs = nodeRefs.map((n) => Ref(createID(n.ref, "node"), ""))
    val newMemberRefs = memberRefs.map((m) => Ref(createID(m.ref, m.`type`), m.role))

    newNodeRefs ++ newMemberRefs
  }
}
