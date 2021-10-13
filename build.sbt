name := "ergo-subpooling"

version := "0.2"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "org.ergoplatform" %% "ergo-appkit" % "develop-3053fde5-SNAPSHOT",
)

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

assemblyJarName in assembly := s"ergo-subpooling-${version.value}.jar"
mainClass in assembly := Some("app.SubPoolingApp")
assemblyOutputPath in assembly := file(s"./ergo-subpooling-${version.value}.jar/")