val mainDeps = Seq(
  "com.typesafe.akka"          %% "akka-stream"              % "2.5.4"
  )

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "be.avhconsult",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT",
      scalacOptions += "-Ypartial-unification"
    )),
    name := "akka-stream-test",
    libraryDependencies ++= mainDeps
  )
