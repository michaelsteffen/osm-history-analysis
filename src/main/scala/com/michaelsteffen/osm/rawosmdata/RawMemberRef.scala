package com.michaelsteffen.osm.rawosmdata

final case class RawMemberRef(
  ref: Long,
  `type`: String, 
  role: String
)