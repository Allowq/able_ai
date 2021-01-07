package testutils

import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, OneInstancePerTest, WordSpecLike}

trait ShapeSpec extends WordSpecLike
  with OneInstancePerTest
  with BeforeAndAfter
  with MockitoSugar {
}
