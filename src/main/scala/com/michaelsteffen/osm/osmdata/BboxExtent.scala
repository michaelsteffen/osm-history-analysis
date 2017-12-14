package com.michaelsteffen.osm.osmdata

// TODO: Handle meridian
final case class BboxExtent(min: Point, max: Point) {
  def union(other: BboxExtent): BboxExtent = BboxExtent(
    min = Point(this.min.lon.min(other.min.lon), this.min.lat.min(other.min.lat)),
    max = Point(this.max.lon.max(other.max.lon), this.max.lat.max(other.max.lat))
  )

  def extendByNode(other: Point): BboxExtent = BboxExtent(
    min = Point(this.min.lon.min(other.lon), this.min.lat.min(other.lat)),
    max = Point(this.max.lon.max(other.lon), this.max.lat.max(other.lat))
  )
}