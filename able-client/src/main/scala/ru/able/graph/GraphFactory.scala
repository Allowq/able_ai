package ru.able.graph

trait GraphFactory[A] {

  def createGraph: A

}
