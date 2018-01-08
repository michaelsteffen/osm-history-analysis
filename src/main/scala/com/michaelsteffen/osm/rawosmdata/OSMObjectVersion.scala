package com.michaelsteffen.osm.rawosmdata

final case class OSMObjectVersion(
  id: Long,
  `type`: String, 
  tags: Map[String,Option[String]],
  lat: Option[BigDecimal],
  lon: Option[BigDecimal],
  nds: List[NodeRef],
  members: List[MemberRef],
  changeset: Long,
  timestamp: java.sql.Timestamp,
  uid: Long,
  user: String,
  version: Long,
  visible: Boolean
)