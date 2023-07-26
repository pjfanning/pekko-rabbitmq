organization := "com.github.pjfanning"
name := "pekko-rabbitmq"

licenses := Seq(
  ("Apache License, Version 2.0",
   url("http://www.apache.org/licenses/LICENSE-2.0")))

homepage := Some(new URL("https://github.com/pjfanning/pekko-rabbitmq"))

ThisBuild / scalaVersion := "2.13.11"

ThisBuild / crossScalaVersions := Seq("2.13.11", "2.12.18", "3.3.0")

def pekko(name: String): ModuleID = "org.apache.pekko" %% s"pekko-$name" % "1.0.1"

libraryDependencies ++= Seq(
  "com.rabbitmq" % "amqp-client" % "5.14.3",
  pekko("actor") % "provided",
  pekko("testkit") % "test",
  "com.typesafe" % "config" % "1.4.2" % Test,
  ("org.specs2" %% "specs2-mock" % "4.13.3" % Test).cross(CrossVersion.for3Use2_13)
)

val scalaReleaseVersion = SettingKey[Int]("scalaReleaseVersion")
scalaReleaseVersion := {
  val v = scalaVersion.value
  CrossVersion.partialVersion(v).map(_._1.toInt).getOrElse {
    throw new RuntimeException(s"could not get Scala release version from $v")
  }
}

Test / unmanagedSourceDirectories ++= {
  if (scalaReleaseVersion.value > 2) {
    Seq(
      (LocalRootProject / baseDirectory).value / "src" / "test" / "scala-3"
    )
  } else {
    Seq(
      (LocalRootProject / baseDirectory).value / "src" / "test" / s"scala-2"
    )
  }
}

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
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
