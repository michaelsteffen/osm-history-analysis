package com.michaelsteffen.osm.osmdata

import scala.math.pow
import com.michaelsteffen.osm.osmdata._

object OSMDataUtils {
  // TODO: look into spire, which will speed up all this exponentiation
  def createID (id: Long, objType: String): Long = {
    if (id >= 2^61) throw new Exception(s"ID out of bounds: $id")
    else {
      objType match {
        case "node" => id
        case "way" => Math.pow(2,61).toLong + id
        case "relation" => Math.pow(2,62).toLong + id
        case _ => throw new Exception(s"Unknown object type: $id")
      }
    }
  }

  def isNode (id: Long): Boolean = id < Math.pow(2,61)

  def isWay (id: Long): Boolean = id >= Math.pow(2,61) && id < Math.pow(2,62)

  def isRelation (id: Long): Boolean = id >= Math.pow(2,62)

  // TODO: define lat/lon for nodes/relations
  private def toOSMObjectVersion (obj: RawOSMObjectVersion): ObjectVersion = OSMObjectVersionDEPRECATED(
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
