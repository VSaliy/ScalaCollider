/*
 *  Connection.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth
package impl

import java.io.{InputStreamReader, BufferedReader, File}
import java.net.InetSocketAddress
import de.sciss.osc.{Message, Client => OSCClient}
import de.sciss.processor.impl.ProcessorImpl
import concurrent.{Await, Promise}
import concurrent.duration._
import de.sciss.processor.Processor
import java.util.concurrent.TimeoutException
import message.{Status, StatusReply}
import de.sciss.osc
import annotation.elidable
import de.sciss.model.impl.ModelImpl
import util.{Failure, Success}
import util.control.NonFatal

private[synth] object ConnectionLike {
  case object Ready
  case object Abort
  case object QueryServer

  final case class AddListener   (l: ServerConnection.Listener)
  final case class RemoveListener(l: ServerConnection.Listener)

  @elidable(elidable.CONFIG) def debug(what: => String) {
    println(s"<connect> $what")
  }
}
import ConnectionLike.debug

private[synth] sealed trait ConnectionLike extends ServerConnection with ModelImpl[ServerConnection.Condition] {
  conn =>

  import ServerConnection.{Running => SCRunning, _}

  def abort(): Unit = {
    Handshake.abort()
    Handshake.value.foreach {
      case Success(s) =>
        s.serverOffline()
        dispatch(Aborted)
      case _ =>
    }
  }

  object Handshake extends ProcessorImpl[ServerImpl, Any] {
    private val beginCond = Promise[Unit]()

    def begin(): Unit = {
      debug("begin")
      beginCond.trySuccess()
    }

    override def notifyAborted(): Unit = {
      debug("notifyAborted")
      beginCond.tryFailure(Processor.Aborted())
    }

    def body(): ServerImpl = {
      debug("body")
      Await.result(beginCond.future, Duration.Inf)

      if (!connectionAlive) throw new IllegalStateException("Connection closed")
      if (!c.isConnected) c.connect()
      ping(message.ServerNotify(on = true)) {
        case Message("/done", "/notify") =>
      }
      val cnt = ping(Status) {
        case m: StatusReply => m
      }
      new ServerImpl(name, c, addr, config, clientConfig, cnt)
    }

    private def ping[A](message: Message)(reply: PartialFunction[osc.Packet, A]): A = {
      val phase = Promise[A]()
      c.action = { p =>
        if (reply.isDefinedAt(p)) phase.trySuccess(reply(p))
      }
      val result = phase.future

      //      // @tailrec broken in 2.10.0, works in 2.10.1
      //      @tailrec def loop(): A = try {
      //        checkAborted()
      //        c ! message
      //        Await.result(result, 500.milliseconds)
      //      } catch {
      //        case _: TimeoutException => loop()
      //      }
      //
      //      loop()

      while (true) try {
        checkAborted()
        c ! message
        val res = Await.result(result, 500.milliseconds)
        return res

      } catch {
        case _: TimeoutException => // loop()
      }
      sys.error("Never here")
    }
  }

  Handshake.addListener {
    case Processor.Result(_, Success(s)) =>
      debug("success")
      dispatch(Preparing(s))
      s.initTree()
      dispatch(SCRunning(s))
      createAliveThread(s)
    case Processor.Result(_, Failure(e)) =>
      e match {
        case Processor.Aborted() =>
          debug("failure: aborted")
        case NonFatal(n) =>
          n.printStackTrace()
      }
      handleAbort()
      dispatch(Aborted)
  }

  def handleAbort(): Unit
  def connectionAlive: Boolean
  def c: OSCClient
  def clientConfig: Client.Config
  def createAliveThread(s: Server): Unit
}

private[synth] final class Connection(val name: String, val c: OSCClient, val addr: InetSocketAddress, val config: Server.Config,
                                      val clientConfig: Client.Config, aliveThread: Boolean)
  extends ConnectionLike {

  import clientConfig.executionContext

  def start(): Unit = {
    Handshake.start()
    Handshake.begin()
  }

  override def toString = "connect<" + name + ">"

  def handleAbort() = ()

  def connectionAlive = true

  // XXX could add a timeout?
  def createAliveThread(s: Server): Unit =
    if (aliveThread) s.startAliveThread(1.0f, 0.25f, 40) // allow for a luxury 10 seconds absence
}

private[synth] final class Booting(val name: String, val c: OSCClient, val addr: InetSocketAddress,
                                   val config: Server.Config, val clientConfig: Client.Config, aliveThread: Boolean)
  extends ConnectionLike {

  import clientConfig.executionContext

  // the actual scsynth system process
  lazy val p: Process = {
    val processArgs = config.toRealtimeArgs
    val directory   = new File(config.programPath).getParentFile
    val pb          = new ProcessBuilder(processArgs: _*)
      .directory(directory)
      .redirectErrorStream(true)
    pb.start()  // throws IOException if command not found or not executable
  }

  // an auxiliary thread that monitors the scsynth process
  val processThread: Thread = new Thread {
    // __do not++ set this to daemon, because that way, the JVM exits while booting. we
    // need at least one non-daemon thread.

    // setDaemon(true)
    override def run(): Unit =
      try {
        debug("enter waitFor")
        val res = p.waitFor()
        debug("exit waitFor")
        println(s"scsynth terminated ($res)")
      } catch {
        case e: InterruptedException =>
          debug("InterruptedException")
          p.destroy()
      } finally {
        debug("abort")
        abort()
      }
  }

  def start(): Unit = {
    // a thread the pipes the scsynth process output to the standard console
    val postThread = new Thread {
      setDaemon(true)
      override def run(): Unit = {
        debug("postThread")
        val inReader  = new BufferedReader(new InputStreamReader(p.getInputStream))
        var isOpen    = true
        var isBooting = true
        try {
          while (isOpen && isBooting) {
            val line = inReader.readLine()
            isOpen = line != null
            if (isOpen) {
              println(line)
              // of course some sucker screwed it up and added another period in SC 3.4.4
              //                        if( line == "SuperCollider 3 server ready." ) isBooting = false
              // one more... this should allow for debug versions and supernova to be detected, too
              if (line.startsWith("Super") && line.contains(" ready")) isBooting = false
            }
          }
        } catch {
          case NonFatal(e) => isOpen = false
        }
        if (isOpen) { // if `false`, `processThread` will terminate and invoke `abort()`
          debug("isOpen")
          Handshake.begin()
        }
        while (isOpen) {
          val line = inReader.readLine
          isOpen = line != null
          if (isOpen) println(line)
        }
      }
    }

    // make sure to begin with firing up Handshake, so that an immediate
    // failure of precessThread does not call abort prematurely
    debug("start")
    Handshake    .start()
    postThread   .start()
    processThread.start()
  }

  override def toString = "boot<" + name + ">"

  def handleAbort()  : Unit     = processThread.interrupt()
  def connectionAlive: Boolean  = processThread.isAlive

  def createAliveThread(s: Server): Unit =
    // note that we optimistically assume that if we boot the server, it
    // will not die (exhausting deathBounces). if it crashes, the boot
    // thread's process will know anyway. this way we avoid stupid
    // server offline notifications when using slow asynchronous commands
    if (aliveThread) s.startAliveThread(1.0f, 0.25f, Int.MaxValue)
}