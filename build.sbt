organization := "com.github.pjfanning"
name := "pekko-rabbitmq"

licenses := Seq(
  ("Apache License, Version 2.0",
   url("http://www.apache.org/licenses/LICENSE-2.0")))

homepage := Some(url("https://github.com/pjfanning/pekko-rabbitmq"))

ThisBuild / scalaVersion := "2.13.18"

ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21", "3.3.9")

def pekko(name: String): ModuleID = "org.apache.pekko" %% s"pekko-$name" % "1.7.1"

libraryDependencies ++= Seq(
  "com.rabbitmq" % "amqp-client" % "5.37.1",
  pekko("actor") % "provided",
  pekko("testkit") % "test",
  "com.typesafe" % "config" % "1.4.10" % Test,
  "org.specs2" %% "specs2-core" % "4.23.0" % Test,
  "org.mockito" % "mockito-core" % "5.24.0" % Test
)

Format.settings

developers := List(
  Developer(id="sbmpost", name="Stefan Post", email="", url=url("https://github.com/sbmpost")),
  Developer(id="gertjana", name="Gertjan Assies", email="", url=url("https://github.com/gertjana")),
  Developer(id="reinierl", name="Reinier Lamers", email="", url=url("https://github.com/reinierl")),
  Developer(id="t3hnar", name="Yaroslav Klymko", email="", url=url("https://github.com/t3hnar")),
  Developer(id="pjfanning", name="PJ Fanning", email="", url=url("https://github.com/pjfanning"))
)

scmInfo := Some(
  ScmInfo(
    url("https://github.com/pjfanning/pekko-rabbitmq"),
    "scm:git@github.com:pjfanning/pekko-rabbitmq.git"
  )
)

publishMavenStyle := true
// Sonatype Central Portal publishing (built into sbt 1.11+)
// release: sbt +publishSigned sonaRelease
publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
