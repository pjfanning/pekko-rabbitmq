organization := "com.github.pjfanning"
name := "akka-rabbitmq"

licenses := Seq(
  ("Apache License, Version 2.0",
   url("http://www.apache.org/licenses/LICENSE-2.0")))

homepage := Some(new URL("https://github.com/pjfanning/akka-rabbitmq"))

ThisBuild / scalaVersion := "2.13.8"

ThisBuild / crossScalaVersions := Seq("2.13.8", "2.12.15", "3.1.1-RC2")

def akka(name: String): ModuleID = "com.typesafe.akka" %% s"akka-$name" % "2.6.+"

libraryDependencies ++= Seq(
  "com.rabbitmq" % "amqp-client" % "5.14.0",
  akka("actor") % "provided",
  akka("testkit") % "test",
  "com.typesafe" % "config" % "1.4.1" % Test,
  ("org.specs2" %% "specs2-mock" % "4.13.1" % Test).cross(CrossVersion.for3Use2_13)
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

publishMavenStyle := true
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
