package com.github.lucastorri.akka.demo.util

import scala.util.Random

trait Identification {

  val id = Random.alphanumeric.take(8).mkString

}
