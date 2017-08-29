credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers += "AWV Nexus" at "https://collab.mow.vlaanderen.be/nexus/content/groups/public"


val mainDeps = Seq(
  "com.typesafe.akka"          %% "akka-stream"              % "2.5.4",
  "be.wegenenverkeer"          %% "rxhttpclient-scala"       % "0.5.2"
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
