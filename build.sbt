name := "autobreaker"

organization := "com.unstablebuild"

organizationName := "unstablebuild.com"

version := "0.5.0"

homepage := Some(url("https://github.com/lucastorri/autobreaker"))

organizationHomepage := Some(url("http://unstablebuild.com"))

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

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := (_ => false)

pomExtra := (
  <scm>
    <url>git@github.com:lucastorri/autobreaker.git</url>
    <connection>scm:git:git@github.com:lucastorri/autobreaker.git</connection>
  </scm>
  <developers>
    <developer>
      <id>lucastorri</id>
      <name>Lucas Torri</name>
      <url>http://unstablebuild.com</url>
    </developer>
  </developers>
)