package com.michaelsteffen.osm.osmdata

import java.sql.Timestamp

import com.michaelsteffen.osm.osmdata.Ref
import com.michaelsteffen.osm.testspecs._

class ObjectVersionTest extends UnitTest {
  describe("An OSMObjectVersion") {
    val baseVersion =  OSMObjectVersionDEPRECATED(
      majorVersion = 1,
      minorVersion = 0,
      timestamp = new Timestamp(2017, 1, 1, 0, 0, 0, 0),
      visible = true,
      tags = Map.empty[String, Option[String]],
      lat = Some(-77.2),
      lon = Some(38.9),
      children = List.empty[Ref],
      parents = List.empty[String],
      changeset = 200
    )

    describe("without tags") {
      it("should not be a feature") {
        assert(baseVersion.isFeature === false)
      }
    }

    describe("with only a type=multipolygon tag") {
      it("should not be a feature") {
        val v = baseVersion.copy(tags = Map("type" ->  Some("multipolygon")))
        assert(v.isFeature === false)
      }
    }

    describe("with a type=multipolygon tag and one or more other tags") {
      it("should be a feature" ) {
        val v = baseVersion.copy(tags = Map("type" ->  Some("multipolygon"), "building" ->  Some("yes")))
        assert(v.isFeature === true)
      }
    }

    describe("without a type=multipolygon tag and with one or more other tags") {
      it("should be a feature if the other tag is type=*" ) {
        val v = baseVersion.copy(tags = Map("type" -> Some("route")))
        assert(v.isFeature === true)
      }
      it("should be a feature if the other tag is *=*" ) {
        val v = baseVersion.copy(tags = Map("building" -> Some("yes")))
        assert(v.isFeature === true)
      }
    }
  }
}

