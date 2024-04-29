
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.1"

run / javaOptions += "-Djava.net.preferIPv4Stack=true"

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)


dockerBaseImage := "openjdk:23-slim"
dockerExposedPorts := Seq(8082)
dockerBuildCommand := {
  if (sys.props("os.arch") != "amd64") {
    // use buildx with platform to build supported amd64 images on other CPU architectures
    // this may require that you have first run 'docker buildx create' to set docker buildx up
    dockerExecCommand.value ++ Seq("buildx", "build", "--platform=linux/amd64", "--load") ++ dockerBuildOptions.value :+ "."
  } else dockerBuildCommand.value
}

lazy val root = (project in file("."))
  .settings(
    name := "ChatbotProxy",
    idePackagePrefix := Some("org.quantum.application")
  )

libraryDependencies += "dev.zio" %% "zio" % "2.1.0-RC3"
libraryDependencies += "dev.zio" %% "zio-http" % "3.0.0-RC6"