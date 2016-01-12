package puck.gui
package svg

import puck.graph._
import puck.gui.svg.actions.{AutoSolveAction, ManualSolveAction}
import javax.swing._

object SVGNodeMenu{

  def apply(controller : SVGController,
            nodeId : NodeId) : JPopupMenu =
    controller.graphStack.graph.getNode(nodeId) match {
      case n : ConcreteNode => new SVGConcreteNodeMenu(controller, n)
      case n : VirtualNode => new VirtualNodeMenu(controller,
        controller.graphStack.graph,
        controller.graphUtils, n)
    }
}

class SVGConcreteNodeMenu
(controller: SVGController,
 node : ConcreteNode)
  extends ConcreteNodeMenu(controller,
    controller.graphStack.graph,
    controller.graphUtils,
    controller.selectedNodes,
    controller.selectedEdge, node) {

  import controller.printingOptionsControl

  override def init() = {
    super.init()

    if (graph.isWronglyContained(node.id)
      || graph.isWronglyUsed(node.id)) {
      this.add(new ManualSolveAction(controller, node))
      this.add(new AutoSolveAction(controller, node,
        printingOptionsControl.printingOptions))
    }

    this.addSeparator()
    addShowOptions()
  }

  private def addShowOptions() : Unit = {

    this.addMenuItem("Infos"){ _ =>
      controller.showNodeInfos(node.id)
    }

    this.addMenuItem("Hide") { _ =>
      printingOptionsControl.hide(graph, node.id)
    }
    this.addMenuItem("Focus") { _ =>
      printingOptionsControl.focusExpand(graph, node.id, focus = true, expand = false)
    }
    this.addMenuItem("Focus & Expand") { _ =>
      printingOptionsControl.focusExpand(graph, node.id, focus = true, expand = true)
    }
    this.addMenuItem("Show code") { _ =>
      controller.printCode(node.id)
    }

    this.addMenuItem("Show abstractions") { _ =>
      controller.printAbstractions(node.id)
    }

    if (graph.content(node.id).nonEmpty) {
      this.addMenuItem("Collapse") { _ =>
        printingOptionsControl.collapse(graph, node.id)
      }
      this.addMenuItem("Expand") { _ =>
        printingOptionsControl.focusExpand(graph, node.id, focus = false, expand = true)
      }
      this.addMenuItem("Expand all") { _ =>
        printingOptionsControl.expandAll(graph, node.id)
      };()
    }
  }
}


