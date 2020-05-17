package ru.able.utils

object common {
  import java.io.File
  import scala.util.matching.Regex

  def describeModel(modelPath: String): Option[String] = {
    def go(t: File, r: Regex): Array[File] = {
      val these = t.listFiles()
      val aim = these.filter(f => r.findFirstIn(f.getName).isDefined)
      aim ++ these.filter(_.isDirectory).flatMap(go(_, r))
    }

    val ext = """.*\.pb$""".r
    val files = go(new File(modelPath), ext)
    files match {
      case f => Some(f.head.getAbsolutePath)
      case _ => None
    }
  }
}
