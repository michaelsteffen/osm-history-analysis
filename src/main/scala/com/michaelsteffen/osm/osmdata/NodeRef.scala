package com.michaelsteffen.osm.osmdata

final case class NodeRef(
  ref: Long
) {
  def toMember: MemberRef = MemberRef(
    ref = this.ref,
    `type` = "node",
    role = ""
  )
}