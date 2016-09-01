name := "autobreaker"

organization := "com.unstablebuild"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.10.6", scalaVersion.value)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.9",
  "io.zman" %% "atmos" % "2.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)

resolvers ++= Seq(
)

licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT"))
