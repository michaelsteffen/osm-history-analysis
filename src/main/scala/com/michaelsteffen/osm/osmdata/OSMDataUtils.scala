package com.michaelsteffen.osm.osmdata

object OSMDataUtils {
  // TODO: look into spire, which should speed up all this exponentiation
  def createID (id: BigInt, objType: String): Long = {
    if (id >= BigInt(Math.pow(2,61).toLong)) throw new Exception(s"ID out of bounds: $id")
    else {
      objType match {
        case "node" => id.toLong
        case "way" => Math.pow(2,61).toLong + id.toLong
        case "relation" => Math.pow(2,62).toLong + id.toLong
        case _ => throw new Exception(s"Unknown object type: $id")
      }
    }
  }

  def isNode (id: Long): Boolean = id < Math.pow(2,61)

  def isWay (id: Long): Boolean = id >= Math.pow(2,61) && id < Math.pow(2,62)

  def isRelation (id: Long): Boolean = id >= Math.pow(2,62)
}
