name := "OpenStreetMap History Analysis"
version := "0.1"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.2.0" % "provided",
  "org.apache.spark" %% "spark-sql" % "2.2.0" % "provided",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test"
)

assemblyJarName in assembly := "osm-history-analysis.jar"
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

logBuffered in Test := false