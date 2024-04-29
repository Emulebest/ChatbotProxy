ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.1"

lazy val root = (project in file("."))
  .settings(
    name := "ChatbotProxy",
    idePackagePrefix := Some("org.quantum.application")
  )

libraryDependencies += "dev.zio" %% "zio" % "2.1.0-RC3"
libraryDependencies += "dev.zio" %% "zio-http" % "3.0.0-RC6"