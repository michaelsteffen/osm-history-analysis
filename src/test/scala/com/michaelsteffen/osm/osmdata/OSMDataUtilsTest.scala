package com.michaelsteffen.osm.osmdata

import com.michaelsteffen.osm.testspecs._
import com.michaelsteffen.osm.rawosmdata._

class OSMDataUtilsTest extends UnitTest {
  describe("The createID method") {
    it("should create a correct id for a node") {
      assert(OSMDataUtils.createID(100, "node") === "n100")
    }

    it("should create a correct id for a way") {
      assert(OSMDataUtils.createID(101, "way") === "w101")
    }

    it("should create a correct id for a relation") {
      assert(OSMDataUtils.createID(102, "relation") === "r102")
    }

    it("should create a ?-prefixed id for any unknown type") {
      assert(OSMDataUtils.createID(103, "anything") === "?103")
    }

    it("should create a ?-prefixed id for an empty (\"\") type") {
      assert(OSMDataUtils.createID(104, "") === "?104")
    }
  }

  describe("The toOSMObjectHistory method") {

    class Fixtures {
      val baseNodeRawVersion = RawOSMObjectVersion(
        id = 100,
        `type` =  "n",
        tags = Map.empty[String,Option[String]],
        lat = Some(-77.1546602),
        lon = Some(38.8935128),
        nds = List.empty[RawNodeRef],
        members = List.empty[RawMemberRef],
        changeset = 200,
        timestamp = new java.sql.Timestamp(2017, 1, 1, 0, 0, 0, 0),
        uid = 300,
        user = "OSMUser",
        version = 1,
        visible = true
      )

      val baseOSMObjectHistory = OSMObjectHistory(
        objType = "n",
        id = "n100",
        versions = List(
          OSMObjectVersion(
            majorVersion = 1,
            minorVersion = 0,
            timestamp = new java.sql.Timestamp(2017, 1, 1, 0, 0, 0, 0),
            visible = true,
            primaryFeatureTypes = List.empty[String],
            tags = Map.empty[String,Option[String]],
            lat = Some(-77.1546602),
            lon = Some(38.8935128),
            children = List.empty[Ref],
            parents = List.empty[String],
            changeset = 200
          )
        )
      )
    }

    def fixtures = new Fixtures

    it("should correctly handle a simple, single-version node") {
      val f = fixtures
      assert(
        OSMDataUtils.toOSMObjectHistory("n100", Iterator(f.baseNodeRawVersion)) ===
        f.baseOSMObjectHistory
      )
    }

    it("should correctly sort versions by timestamp") {
    }

    describe("when extracting primary feature types") {
      it("should correctly handle a feature with no primary feature tags") {
      }

      it("should correctly handle a feature with one primary feature tag") {
      }

      it("should correctly handle a feature with multiple primary feature tags") {
      }
    }

    describe("when converting child references") {
      it("should correctly handle way->node references") {
      }

      it("should correctly handle relation refs") {
      }
    }
  }
}
