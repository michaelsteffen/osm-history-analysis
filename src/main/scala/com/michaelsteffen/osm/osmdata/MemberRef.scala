package com.michaelsteffen.osm.osmdata

final case class MemberRef(
  `type`: String,
  ref: BigInt,
  role: String
)