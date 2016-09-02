lazy val commonSettings = Seq(
  organization := "com.example",
  version := "0.5.2",
  scalaVersion := "2.11.8",
  organization := "com.unstablebuild",
  organizationName := "unstablebuild.com",
  homepage := Some(url("https://github.com/lucastorri/autobreaker")),
  organizationHomepage := Some(url("http://unstablebuild.com")),
  crossScalaVersions := Seq(scalaVersion.value),
  licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT")),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  ),
  resolvers ++= Seq(),
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := (_ => false),
  pomExtra :=
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

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "autobreaker",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.4.9",
      "io.zman" %% "atmos" % "2.1",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"
    )
  )

lazy val guice = project.in(file("guice"))
  .dependsOn(root)
  .settings(commonSettings: _*)
  .settings(
    name := "autobreaker-guice",
    libraryDependencies ++= Seq(
      "com.google.inject" % "guice" % "4.1.0"
    )
  )
