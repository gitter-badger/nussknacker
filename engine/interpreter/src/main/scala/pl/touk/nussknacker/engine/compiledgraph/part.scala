package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.definition.CustomNodeInvoker
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph.end.{End, NormalEnd}
import pl.touk.nussknacker.engine.splittedgraph.splittednode
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

object part {

  sealed trait ProcessPart {
    type T <: NodeData
    def node: SplittedNode[T]
    def id: String = node.id
  }

  case class SourcePart(obj: api.process.Source[Any], node: splittednode.SourceNode,
                        nextParts: List[SubsequentPart], ends: List[End]) extends ProcessPart {
    override type T = StartingNodeData
  }

  sealed trait SubsequentPart extends ProcessPart

  case class CustomNodePart(customNodeInvoker: CustomNodeInvoker[_],
                            node: splittednode.OneOutputSubsequentNode[CustomNode],
                            nextParts: List[SubsequentPart], ends: List[End]) extends SubsequentPart {
    override type T = CustomNode

  }

  case class SplitPart(node: splittednode.SplitNode, nexts: List[NextWithParts]) extends SubsequentPart {
    override type T = Split
  }

  case class SinkPart(obj: api.process.Sink, node: splittednode.EndingNode[Sink]) extends SubsequentPart {
    override type T = Sink

    val ends = List(NormalEnd(node.id))
  }

  case class NextWithParts(next: splittednode.Next, nextParts: List[SubsequentPart], ends: List[End])

}