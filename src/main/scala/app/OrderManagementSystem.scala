package app

import java.util.UUID

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import app.Order.Place
import app.OrderManagementSystem.PlaceOrder

import scala.concurrent.duration._

object Main extends App {

  val system: ActorSystem[OrderManagementSystem.Command] =
    ActorSystem(OrderManagementSystem(), "oms")

  system ! PlaceOrder(UUID.randomUUID().toString)
}

object OrderManagementSystem {

  sealed trait Command
  final case class PlaceOrder(orderId: String) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case PlaceOrder(orderId) =>
        val order = context.spawn(Order(orderId), s"order-$orderId")
        order ! Place()
        Behaviors.same
      case _ => Behaviors.unhandled
    }
  }
}

object Order {

  sealed trait Command

  final case class Place() extends Command

  final case class Assign(courierId: String) extends Command

  final case class AssignmentTimeout() extends Command

  final case class UnAssign() extends Command

  final case class PickUp() extends Command

  final case class DropOff() extends Command

  final case class Cancel() extends Command

  def apply(orderId: String): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case Place() =>
        context.log.info(s"Order $orderId is placed")
        placed(orderId)
      case unexpectedCommand =>
        context.log.warn(s"Unexpected command $unexpectedCommand for order $orderId in initial state")
        Behaviors.unhandled
    }
  }

  def placed(orderId: String): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers[Command] { timers =>
      // send for assignment
      val timeout = 5.seconds
      context.log.info(s"Assignment will time-out in ${timeout.toSeconds} seconds")
      timers.startSingleTimer(AssignmentTimeout(), timeout)
      Behaviors.receiveMessage {
        case Assign(courierId) =>
          context.log.info(s"Order $orderId is assigned to courier $courierId")
          assigned(orderId, courierId)
        case AssignmentTimeout() =>
          context.log.info(s"Order $orderId assignment timed-out")
          Behaviors.stopped
        case Cancel() =>
          context.log.info(s"Order $orderId is cancelled")
          Behaviors.stopped
        case unexpectedCommand =>
          context.log.warn(s"Unexpected command $unexpectedCommand for order $orderId in placed state")
          Behaviors.unhandled
      }
    }
  }

  def assigned(orderId: String, courierId: String): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case PickUp() =>
        context.log.info(s"Order $orderId is picked-up by courier $courierId")
        pickedUp(orderId, courierId)
      case UnAssign() =>
        context.log.info(s"Order $orderId is un-assigned from courier $courierId")
        placed(orderId)
      case Cancel() =>
        context.log.info(s"Order $orderId is cancelled by customer, un-assigning courier $courierId")
        // un-assign courier
        Behaviors.stopped
      case unexpectedCommand =>
        context.log.warn(s"Unexpected command $unexpectedCommand for order $orderId in assigned to courier $courierId state")
        Behaviors.unhandled
    }
  }

  def pickedUp(orderId: String, courierId: String): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case DropOff() =>
        context.log.info(s"Order $orderId is dropped-off by courier $courierId")
        Behaviors.stopped
      case Cancel() =>
        context.log.info(s"Order $orderId is cancelled bu customer, un-assigning courier $courierId")
        Behaviors.stopped
      case unexpectedCommand =>
        context.log.warn(s"Unexpected command $unexpectedCommand for order $orderId in picked-up by courier $courierId state")
        Behaviors.unhandled
    }
  }
}
