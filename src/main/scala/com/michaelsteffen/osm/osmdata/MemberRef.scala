package com.michaelsteffen.osm.osmdata

final case class MemberRef(
  ref: Long,
  `type`: String, 
  role: String
)