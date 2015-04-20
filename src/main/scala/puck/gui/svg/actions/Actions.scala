package puck.gui.svg.actions

import java.awt.event.ActionEvent
import javax.swing.AbstractAction

import puck.PuckError
import puck.graph.{Isa, DGEdge, ConcreteNode}
import puck.gui.svg.SVGController
import puck.javaGraph.JavaTransformationRules

import scalaz._

class AddIsaAction
( sub : ConcreteNode,
  sup : ConcreteNode,
  controller : SVGController)
extends AbstractAction(s"Add ${sub.name} isa ${sup.name}") {

  import controller.graph

  def actionPerformed(e: ActionEvent) : Unit =
    controller.pushGraph(graph.addIsa(sub.id, sup.id))

}
class RemoveEdgeAction
( edge : DGEdge,
  controller : SVGController)
  extends AbstractAction(s"Delete node and children") {

  def actionPerformed(e: ActionEvent) : Unit =
    printErrOrPushGraph(controller, "Remove Node Action failure"){
      edge.kind match {
        case Isa => \/-(edge.deleteIn(controller.graph))
        case _ => -\/(new PuckError(s"cannot remove remove ${edge.kind} edge"))
      }


    }
}

class RemoveNodeAction
( node : ConcreteNode,
  controller : SVGController)
extends AbstractAction(s"Delete node and children") {

  import controller.graph

  def actionPerformed(e: ActionEvent) : Unit =
    printErrOrPushGraph(controller, "Remove Node Action failure"){
      JavaTransformationRules.removeConcreteNode(graph, node)
    }
}



class RenameNodeAction
( node : ConcreteNode,
  controller : SVGController )
  extends AbstractAction("Rename") {

  import controller.graph
  override def actionPerformed(e: ActionEvent): Unit = {
    showInputDialog("New name:").foreach {
      newName =>
          controller.pushGraph(graph.setName(node.id, newName))
        /*graph.kindType(node.id) match {
          case TypeMember =>
          case _ =>
        }*/

    }
  }

}