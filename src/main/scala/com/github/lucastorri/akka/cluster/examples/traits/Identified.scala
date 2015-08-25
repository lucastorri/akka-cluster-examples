package com.github.lucastorri.akka.cluster.examples.traits

import scala.util.Random

trait Identified {

  val id = Random.alphanumeric.take(8).mkString

}
