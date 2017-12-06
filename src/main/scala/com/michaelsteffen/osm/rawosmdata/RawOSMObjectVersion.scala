package com.michaelsteffen.osm.rawosmdata

final case class RawOSMObjectVersion(
  id: Long,
  `type`: String, 
  tags: Map[String,Option[String]],
  lat: Option[BigDecimal],
  lon: Option[BigDecimal],
  nds: List[RawNodeRef],
  members: List[RawMemberRef],
  changeset: Long,
  timestamp: java.sql.Timestamp,
  uid: Long,
  user: String,
  version: Long,
  visible: Boolean
)