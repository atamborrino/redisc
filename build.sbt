name := "redisc"

organization := "com.github.atamborrino"

version := "0.1-SNAPSHOT"
 
scalaVersion := "2.10.1"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2-M3",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test" 
)

