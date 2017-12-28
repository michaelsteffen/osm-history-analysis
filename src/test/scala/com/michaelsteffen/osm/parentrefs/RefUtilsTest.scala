package com.michaelsteffen.osm.parentrefs

import com.michaelsteffen.osm.testspecs.UnitTest

class RefUtilsTest extends UnitTest {
  describe("The generateRefChangesFromObjectHistory method") {
    ignore("should generate an empty history for an object with only one version") {}
    ignore("should detect the addition of a node from a way") {}
    ignore("should detect the addition of a member from a relation") {}
    ignore("should detect the removal of a node from a way") {}
    ignore("should detect the removal of a member from a relation") {}
    ignore("should detect the addition of multiple children") {}
    ignore("should detect the removal of multiple children") {}
    ignore("should detect the addition and removal of children in the same change") {}
  }

  describe("The collectRefChangesForChild method") {
    ignore("should sort ref changes by timestamp") {}
  }

  describe("The addParentRefs method") {
    ignore("should return an unmodified object history if the RefChangeGroupToPropagate is null") {}
    ignore("should return an unmodified object history if the RefChangeGroupToPropagate is empty") {}
    ignore("should correctly add parent refs to the first version of an object") {}
    describe("when handling parent ref changes contemporaneous with creation of a new major version of an object") {
      ignore("should add new parent refs to the major version of the object") {}
      ignore("should remove deleted parent refs from the major version of the object") {}
    }
    describe("when handling parent ref changes between major versions of an object") {
      ignore("should create a minor version of the object, adding new parent refs") {}
      ignore("should create a minor version of the object, excluding deleted parent refs") {}
      ignore("should consolidate contemporaneous parent changes into the same minor version of the object") {}
      ignore("should create multiple minor version of the object as needed") {}
    }
  }
}
