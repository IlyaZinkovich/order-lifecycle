name := "order-lifecycle"

version := "0.1"

scalaVersion := "2.12.4"

val AkkaVersion = "2.6.8"
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
