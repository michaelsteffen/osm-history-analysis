package com.michaelsteffen.osm.osmdata

final case class NodeRef(
  ref: BigInt
) {
  def toMember: MemberRef = MemberRef(
    ref = this.ref,
    `type` = "node",
    role = ""
  )
}