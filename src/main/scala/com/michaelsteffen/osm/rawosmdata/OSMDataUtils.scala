package com.michaelsteffen.osm.rawosmdata

import com.michaelsteffen.osm.osmdata._

object OSMDataUtils {
  def toOSMObjectHistory (id: String, rawObjHistory: Iterator[RawOSMObjectVersion]): OSMObjectHistory = OSMObjectHistory(
    id = id,
    objType = id(0).toString,
    versions = rawObjHistory
      .toList
      .sortWith(_.timestamp.getTime < _.timestamp.getTime)
      .map(toOSMObjectVersion)
  )

  def createID (id: Long, objType: String): Long = {
    if (id >= 2^61) throw new Exception(s"ID out of bounds: $id")
    else {
      objType match {
        case "node" => id
        case "way" => 2^61 + id
        case "relation" => 2^62 + id
        case _ => throw new Exception(s"Unknown object type: $id")
      }
    }
  }

  def hasGeometry (objType: String, objVersion: OSMObjectVersion): Boolean = {
    objVersion.isFeature && (objType match {
      case "n" | "w" => true
      case "r" => objVersion.tags.getOrElse("type", "").equals("multipolygon")
    })
  }

  // TODO: define lat/lon for nodes/relations
  private def toOSMObjectVersion (obj: RawOSMObjectVersion): OSMObjectVersion = OSMObjectVersionDEPRECATED(
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

  private def convertRefs (nodeRefs: List[RawNodeRef], memberRefs: List[RawMemberRef]): List[Ref] = {
    val newNodeRefs = nodeRefs.map(n => Ref(createID(n.ref, "node"), ""))
    val newMemberRefs = memberRefs.map(m => Ref(createID(m.ref, m.`type`), m.role))

    newNodeRefs ++ newMemberRefs
  }
}
