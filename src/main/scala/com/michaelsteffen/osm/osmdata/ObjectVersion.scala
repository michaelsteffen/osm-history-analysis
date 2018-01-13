package com.michaelsteffen.osm.osmdata

final case class ObjectVersion(
  id: BigInt,
  `type`: String, 
  tags: Map[String,Option[String]],
  lat: Option[BigDecimal],
  lon: Option[BigDecimal],
  nds: List[NodeRef],
  members: List[MemberRef],
  changeset: BigInt,
  timestamp: java.sql.Timestamp,
  uid: BigInt,
  user: String,
  version: BigInt,
  visible: Boolean
) {
  def isFeature: Boolean = !(this.tags.isEmpty || this.tags == Map("type" -> Some("multipolygon")))

  def hasGeometry: Boolean = {
    this.isFeature && (this.`type` match {
      case "node" | "way" => true
      case "relation" => this.tags.getOrElse("type", "").equals("multipolygon")
      case "" => false
    })
  }

  def children: List[MemberRef] = this.`type` match {
    case "" | "node" => List.empty[MemberRef]
    case "way" => this.nds.map(_.toMember)
    case "relation" => this.members
  }
}

object ObjectVersion {
  def empty: ObjectVersion = ObjectVersion(
    id = 0,
    `type` = "",
    tags = Map.empty[String,Option[String]],
    lat = None,
    lon = None,
    nds = List.empty[NodeRef],
    members = List.empty[MemberRef],
    changeset = 0,
    timestamp = new java.sql.Timestamp(0),
    uid = 0,
    user = "",
    version = 0,
    visible = false
  )
}