package puck.javaGraph

import java.io.{File, InputStream}

import puck.graph._
import puck.graph.transformations.{NodeMappingInitialState, Recording}
import puck.util.{PuckNoopLogger, PuckLogger, FileHelper}



/**
 * Created by lorilan on 22/02/15.
 */
object CompileHelper {

  def apply(sources: List[String], jars: List[String]): Option[AST.Program] = {
      val arglist = createArglist(sources, jars, List())

      val f = new AST.Frontend {
        protected override def processWarnings(errors: java.util.Collection[_], unit: AST.CompilationUnit): Unit = {
        }
      }
      val br = new AST.BytecodeParser
      val jp = new AST.JavaParser {
        def parse(is: InputStream, fileName: String): AST.CompilationUnit = {
          (new parser.JavaParser).parse(is, fileName)
        }
      }

    if (f.process(arglist, br, jp)){
      Some(f.getProgram)}
    else
      None
  }

  def buildGraph(p : AST.Program,
                 logger : PuckLogger = PuckNoopLogger,
                 ll : AST.LoadingListener = null )  :
  (AST.Program, DependencyGraph, Recording, Map[String, NodeId], Map[NodeId, ASTNodeLink]) = {

    val builder = p.buildAccessGraph(null, ll)
    builder.attachOrphanNodes()
    builder.registerSuperTypes()

    val (_, transfos) = NodeMappingInitialState.normalizeNodeTransfos(builder.g.recording(), Seq())
    val initialRecord = new Recording(transfos)

    val g = builder.g.newGraph(nRecording = Recording())
              .withLogger(logger)

    (p, g,
      initialRecord,
      builder.nodesByName,
      builder.graph2ASTMap)
  }

  def compileSrcsAndbuildGraph(sources: List[String],
                 jars: List[String],
                 decouple : Option[java.io.File] = None) :
    (AST.Program, DependencyGraph, Recording, Map[String, NodeId], Map[NodeId, ASTNodeLink]) =
    this.apply(sources, jars) match {
      case None => throw new AGBuildingError("Compilation error, no AST generated")
      case Some(p) => buildGraph(p)
    }



  private[puck] def createArglist(sources: List[String],
                                  jars: List[String],
                                  srcdirs:List[String]): Array[String] = {

    if (jars.isEmpty) sources.toArray
    else {
      val args: List[String] = "-classpath" :: jars.mkString("", File.pathSeparator, File.pathSeparator + ".") :: (
        if (srcdirs.isEmpty) sources
        else "-sourcepath" :: srcdirs.mkString("", File.pathSeparator, File.pathSeparator + ".") :: sources)
      args.toArray
    }
  }
}
