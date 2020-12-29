import akka.actor.Cancellable
import akka.stream.scaladsl.Source

package object ru {
  type TickSource = Source[Int, Cancellable]
}
