package puck.gui.search.decisionsFrames

import puck.graph.{NodeKind, DGNode}
import puck.graph.constraints.AbstractionPolicy

import scala.swing._
import scala.swing.event.{SelectionChanged, Event}
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.Success

/**
 * Created by lorilan on 04/06/14.
 */

object AbstractionKindAndPolicyChooser{

  def apply(impl : DGNode) : (NodeKind, AbstractionPolicy) = DecisionFrame {
    () => new AbstractionKindAndPolicyChooser(impl)
  }
}

class AbstractionKindAndPolicyChooser private (val impl : DGNode)
  extends DecisionFrame[(NodeKind, AbstractionPolicy)]{


  title = "Choose abstraction kind and policy"

  val policyChoice = new ComboBox(impl.kind.abstractionPolicies)
  var absPolicy : AbstractionPolicy = policyChoice.selection.item
  var kindChoice = new ComboBox(impl.kind.abstractKinds(absPolicy))
  var absKind : NodeKind = kindChoice.selection.item

  val kindChoiceWrapper = new FlowPanel(){ contents += kindChoice}
  listenTo(policyChoice.selection)

  reactions += {
    case SelectionChanged(source) =>
      if(source == policyChoice) {
        absPolicy = policyChoice.selection.item

        kindChoice = new ComboBox(impl.kind.abstractKinds(absPolicy))

        kindChoiceWrapper.contents.clear()
        kindChoiceWrapper.contents += kindChoice
        AbstractionKindAndPolicyChooser.this.pack()
        ()
      }
    //case e : Event => println(e.getClass +" happened !")
  }

  contents = new BoxPanel(Orientation.Vertical) {
    contents += new FlowPanel(){
      contents += new Label("Abstracting " + impl)
    }

    contents +=  new FlowPanel(){
      contents += policyChoice
      contents += kindChoiceWrapper
    }

    /*val default = new DefaultDecisionMaker(impl.graph).abstractionKindAndPolicy(impl)

    contents += new FlowPanel() {
      contents += new Label("Default decision is " + default)
    }*/

    contents += new FlowPanel(){
      /*contents += Button("Default"){
        AbstractionKindAndPolicyChooser.this.complete(default)
      }*/
      contents += Button("OK") {
        AbstractionKindAndPolicyChooser.this.complete((absKind, absPolicy))
      }

    }
  }
}
