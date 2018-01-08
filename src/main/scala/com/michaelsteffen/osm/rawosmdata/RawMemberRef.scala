package com.michaelsteffen.osm.rawosmdata

final case class MemberRef(
  ref: Long,
  `type`: String, 
  role: String
)