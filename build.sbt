name := "actors-cluster-on-akka"

version := "0.1"

scalaVersion := "2.11.7"

scalacOptions := Seq("-deprecation")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding" % "2.4.0-RC1",
  "com.typesafe.akka" %% "akka-contrib" % "2.4.0-RC1",
  "com.typesafe.akka" %% "akka-distributed-data-experimental" % "2.4.0-RC1")
