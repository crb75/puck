package puck

import java.io.{BufferedWriter, FileWriter, File}

import puck.graph.{DotPrinter, AGBuildingError, AccessGraph}
import puck.graph.java.JavaNodeKind
import scala.sys.process.Process

/**
 * Created by lorilan on 08/05/14.
 */
class FilesHandler private (private [this] var srcDir : File,
                            private [this] var jlFile: File,
                            private [this] var anFile :File,
                            private [this] var dcpl : File,
                            private [this] var g: File){


  private [this] var ag : AccessGraph = _
  def accessGraph = ag

  def srcDirectory = this.srcDir
  def srcDirectory_=(dir : File){ this.srcDir = dir.getCanonicalFile }

  def jarListFile = this.jlFile
  def jarListFile_=(f:File){ this.jlFile = f.getCanonicalFile}

  def apiNodesFile = this.anFile
  def apiNodesFile_=(f:File){this.anFile= f.getCanonicalFile}

  private [this] var gdot : File = _
  def graphvizDot = this.gdot
  def graphvizDot_=(f: File){this.gdot = f.getCanonicalFile}

  def decouple = this.dcpl
  def decouple_=(f:File){this.dcpl = f.getCanonicalFile}

  def graph = this.g
  def graph_=(f: File){this.g= f.getCanonicalFile}

  def loadGraph(ll : AST.LoadingListener) : AccessGraph = {
    FilesHandler.compile(FilesHandler.findAllJavaFiles(this.srcDirectory),
      puck.fileLines(jarListFile)) match {
      case None => throw new AGBuildingError("Compilation error, no AST generated")
      case Some(p) =>
        ag = p.buildAccessGraph(puck.initStringLiteralsMap(decouple), ll)
        fileLines(apiNodesFile).foreach {
          (l: String) =>
            val tab = l.split(" ")
            ag.addApiNode(p, tab(0), tab(1), tab(2))
        }
        ag.attachNodesWithoutContainer()
        ag
    }
  }

  def makeDot(printId : Boolean = false){
    DotPrinter.print(new BufferedWriter(new FileWriter(graph.getCanonicalPath+".dot")),
      ag, JavaNodeKind, printId)
  }

  def dot2png() : Int = {
    val processBuilder = Process(List(
      if(graphvizDot == null) "dot"  // relies on dot directory being in the PATH variable
      else graphvizDot.getCanonicalPath, "-Tpng", graph.getCanonicalPath+".dot"))

    processBuilder #> new File(graph.getCanonicalPath+".png")

    processBuilder.!
  }
    /*
        File f = plHandler.getDecouple();

        Map<String,Collection<BodyDecl>> allStringUses = null;

        if(f.exists()){
            allStringUses = Utils.initStringLiteralsMap(f);
        }
    */


}

object FilesHandler{
  final val defaultDecoupleFileName: String = "decouple.pl"
  final val defaultGraphFileName: String = "graph"
  final val defaultJarListFileName: String = "jar.list"
  final val defaultApiNodesFileName: String = "api_nodes"

  private def defaultFile(dir:File, file: File) =
    new File(dir.getCanonicalFile + File.separator + file)

  def apply(srcDir: File)(jarListFile: File =
                          defaultFile(srcDir, defaultJarListFileName),
                          apiNodesFile :File =
                          defaultFile(srcDir, defaultApiNodesFileName),
                          decouple : File =
                          defaultFile(srcDir, defaultDecoupleFileName),
                          graph : File =
                          defaultFile(srcDir, defaultGraphFileName))
  = new FilesHandler(srcDir.getCanonicalFile, jarListFile, apiNodesFile, decouple, graph)

  def apply() : FilesHandler= apply(new File("."))()

  def findAllJavaFiles(f:File) : List[String] = findAllJavaFiles(List(), f)

  def findAllJavaFiles(res: List[String], f: File) : List[String] = {
    if (f.isDirectory)
      f.listFiles().foldLeft(res)(findAllJavaFiles)
    else {
      if (f.getName.endsWith(".java"))
        f.getPath :: res
      else
        res
    }
  }

  def compile(sources: List[String], jars: List[String]): Option[AST.Program] = {
    val arglist = createArglist(sources, jars, None)
    val f = new AST.Frontend {
      protected override def processWarnings(errors: java.util.Collection[_], unit: AST.CompilationUnit) {
      }
    }
    val br = new AST.BytecodeParser
    val jp = new AST.JavaParser {
      def parse(is: java.io.InputStream, fileName: String): AST.CompilationUnit = {
        (new parser.JavaParser).parse(is, fileName)
      }
    }
    if (f.process(arglist, br, jp))
      Some(f.getProgram)
    else
      None
  }

  private[puck] def createArglist(sources: List[String], jars: List[String],
                                  srcdirs:Option[List[String]]): Array[String] = {
    if (jars.length == 0) return sources.toArray

    val args : List[String] = "-classpath" :: jars.mkString("", File.pathSeparator, ".") :: (
      srcdirs match {
        case Some(dirs) =>"-sourcespath" :: dirs.mkString("", ":", ".") :: sources
        case None => sources
      })
    args.toArray
  }

}