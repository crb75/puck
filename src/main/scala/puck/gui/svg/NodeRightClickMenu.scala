package puck.gui.svg

import puck.graph.{Uses, ConcreteNode, DGEdge}
import puck.graph.constraints.{SupertypeAbstraction, DelegationAbstraction}
import puck.gui.svg.actions.AddIsaAction
import puck.gui.svg.actions.MergeAction
import puck.gui.svg.actions.MoveAction
import puck.gui.svg.actions.RedirectAction
import puck.gui.svg.actions.RemoveNodeAction
import puck.gui.svg.actions._
import javax.swing._
import java.awt.event.ActionEvent


class NodeRightClickMenu
( private val controller: SVGController,
  node : ConcreteNode) extends JPopupMenu {

  init()


  def this(controller: SVGController,
           nodeId: Int) =
    this(controller, controller.graph.getConcreteNode(nodeId))

  import controller.graph


  def addMenuItem(name : String)(action : ActionEvent => Unit) = {
    this.add(new AbstractAction(name) {
      def actionPerformed(actionEvent: ActionEvent) : Unit =
        action(actionEvent)
    })
  }


  def init() : Unit = {

    this.add(new RenameNodeAction(node, controller))
    this.addSeparator()

    val item = new JMenuItem(s"Abstract ${node.name} as")
    item.setEnabled(false)
    this.add(item)

    controller.abstractionChoices(node).foreach(this.add)

    val childChoices  = controller.childChoices(node)
    if (childChoices.nonEmpty) {
      this.addSeparator()
      childChoices.foreach(this.add)
    }

    this.addSeparator()
    this.add(new RemoveNodeAction(node, controller))
    if (controller.nodeIsSelected) {
      addOtherNodeSelectedOption()
    }
    if (controller.edgeIsSelected) {
      addEdgeSelectedOption()
    }


    if(graph.isWronglyContained(node.id))
      this.add(new SolveAction(node, controller))

    this.addSeparator()
    addShowOptions()
  }


  private def addAddIsaOption(sub: ConcreteNode, sup: ConcreteNode) : Unit = {
    this.add(new AddIsaAction(sub, sup, controller));()
  }


  private def addOtherNodeSelectedOption() : Unit = {
    val id: Int = controller.getIdNodeSelected
    val selected: ConcreteNode = graph.getConcreteNode(id)
    if (graph.canContain(node, selected)) {
      this.add(new MoveAction(node, selected, controller))
    }

//    val m: MergeMatcher = controller.transfoRules.
//        mergeMatcherInstances.syntaxicMergeMatcher(selected)
//
//    if (m.canBeMergedInto(node, graph))
      this.add(new MergeAction(selected, node, controller))


    if(selected.id != node.id) {
      if (selected.kind.canBe(node.kind)) addAddIsaOption(selected, node)
      if (node.kind.canBe(selected.kind)) addAddIsaOption(node, selected)
    }
  }

  private def addEdgeSelectedOption() : Unit =
    controller.getEdgeSelected match {
      case uses : Uses =>
        this.add(new RedirectAction(node, uses, SupertypeAbstraction, controller))
        this.add(new RedirectAction(node, uses, DelegationAbstraction, controller));()
      case _ => ()
    }


  private def addShowOptions() : Unit = {
    addMenuItem("Hide") { _ =>
      NodeRightClickMenu.this.controller.hide(node.id)
    }
    if (graph.content(node.id).nonEmpty) {
      addMenuItem("Collapse") { _ =>
        NodeRightClickMenu.this.controller.collapse(node.id)
      }
      addMenuItem("Expand") { _ =>
        NodeRightClickMenu.this.controller.expand(node.id)
      }
      addMenuItem("Expand all") { _ =>
        NodeRightClickMenu.this.controller.expandAll(node.id)
      };()
    }
  }
}

