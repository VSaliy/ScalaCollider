/*
 *  ControlMappings.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

import collection.immutable.{IndexedSeq => Vec}
import language.implicitConversions

object ControlSet extends ControlSetValueImplicits with ControlSetVectorImplicits {

  object Value extends ControlSetValueImplicits {
    def apply(key: String, value: Float): Value = new Value(key, value)
    def apply(key: Int   , value: Float): Value = new Value(key, value)
  }
  final case class Value private(key: Any, value: Float)
    extends ControlSet {

    private[sciss] def toSetSeq : IndexedSeq[Any] = scala.Vector(key,    value)
    private[sciss] def toSetnSeq: IndexedSeq[Any] = scala.Vector(key, 1, value)
  }

  object Vector extends ControlSetVectorImplicits {
    def apply(key: String, values: IndexedSeq[Float]): Vector = new Vector(key, values)
    def apply(key: Int   , values: IndexedSeq[Float]): Vector = new Vector(key, values)
  }
  final case class Vector private(key: Any, values: IndexedSeq[Float])
    extends ControlSet {

    private[sciss] def toSetSeq : IndexedSeq[Any] = scala.Vector(key, values)
    private[sciss] def toSetnSeq: IndexedSeq[Any] = key +: values.size +: values
  }
}

sealed trait ControlSet {
  private[sciss] def toSetSeq : IndexedSeq[Any]
  private[sciss] def toSetnSeq: IndexedSeq[Any]
}

private[synth] sealed trait ControlSetValueImplicits {
  implicit def intFloatControlSet    (tup: (Int   , Float )) = ControlSet.Value(tup._1, tup._2)
  implicit def intIntControlSet      (tup: (Int   , Int   )) = ControlSet.Value(tup._1, tup._2.toFloat)
  implicit def intDoubleControlSet   (tup: (Int   , Double)) = ControlSet.Value(tup._1, tup._2.toFloat)
  implicit def stringFloatControlSet (tup: (String, Float )) = ControlSet.Value(tup._1, tup._2)
  implicit def stringIntControlSet   (tup: (String, Int   )) = ControlSet.Value(tup._1, tup._2.toFloat)
  implicit def stringDoubleControlSet(tup: (String, Double)) = ControlSet.Value(tup._1, tup._2.toFloat)
}

private[synth] sealed trait ControlSetVectorImplicits {
  // leaves stuff like ArrayWrapper untouched
  private[this] def carefulIndexed(xs: Seq[Float]): IndexedSeq[Float] = xs match {
    case indexed: IndexedSeq[Float] => indexed
    case _                          => xs.toIndexedSeq
  }

  implicit def intFloatsControlSet   (tup: (Int   , Seq[Float])) = ControlSet.Vector(tup._1, carefulIndexed(tup._2))
  implicit def stringFloatsControlSet(tup: (String, Seq[Float])) = ControlSet.Vector(tup._1, carefulIndexed(tup._2))
}

object ControlKBusMap {
  implicit def intIntControlKBus   (tup: (Int   , Int)) = Single(tup._1, tup._2)
  implicit def stringIntControlKBus(tup: (String, Int)) = Single(tup._1, tup._2)

  /** A mapping from an mono-channel control-rate bus to a synth control. */
  final case class Single(key: Any, index: Int)
    extends ControlKBusMap {

    def toMapSeq : Vec[Any] = Vector(key, index)
    def toMapnSeq: Vec[Any] = Vector(key, index, 1)
  }

  implicit def intKBusControlKBus   (tup: (Int   , ControlBus)) = Multi(tup._1, tup._2.index, tup._2.numChannels)
  implicit def stringKBusControlKBus(tup: (String, ControlBus)) = Multi(tup._1, tup._2.index, tup._2.numChannels)

  /** A mapping from an mono- or multi-channel control-rate bus to a synth control. */
  final case class Multi(key: Any, index: Int, numChannels: Int)
    extends ControlKBusMap {

    def toMapnSeq: Vec[Any] = Vector(key, index, numChannels)
  }
}

/** A mapping from a control-rate bus to a synth control. */
sealed trait ControlKBusMap {
  def toMapnSeq: Vec[Any]
}

object ControlABusMap {
  implicit def intIntControlABus   (tup: (Int   , Int)) = Single(tup._1, tup._2)
  implicit def stringIntControlABus(tup: (String, Int)) = Single(tup._1, tup._2)

  /** A mapping from an mono-channel audio bus to a synth control. */
  final case class Single(key: Any, index: Int)
    extends ControlABusMap {

    private[sciss] def toMapaSeq : Vec[Any] = Vector(key, index)
    private[sciss] def toMapanSeq: Vec[Any] = Vector(key, index, 1)
  }

  implicit def intABusControlABus   (tup: (Int   , AudioBus)) = Multi(tup._1, tup._2.index, tup._2.numChannels)
  implicit def stringABusControlABus(tup: (String, AudioBus)) = Multi(tup._1, tup._2.index, tup._2.numChannels)

  /** A mapping from an mono- or multi-channel audio bus to a synth control. */
  final case class Multi(key: Any, index: Int, numChannels: Int)
    extends ControlABusMap {

    private[sciss] def toMapanSeq: Vec[Any] = Vector(key, index, numChannels)
  }
}

/** A mapping from an audio bus to a synth control.
  *
  * Note that a mapped control acts similar to an `InFeedback` UGen in that it does not matter
  * whether the audio bus was written before the execution of the synth whose control is mapped or not.
  * If it was written before, no delay is introduced, otherwise a delay of one control block is introduced.
  *
  * @see  [[de.sciss.synth.ugen.InFeedback]]
  */
sealed trait ControlABusMap {
  private[sciss] def toMapanSeq: Vec[Any]
}

object ControlFillRange {
  def apply(key: String, numChannels: Int, value: Float) = new ControlFillRange(key, numChannels, value)
  def apply(key: Int   , numChannels: Int, value: Float) = new ControlFillRange(key, numChannels, value)

  implicit def intFloatControlFill    (tup: (Int   , Int, Float )) = apply(tup._1, tup._2, tup._3)
  implicit def intIntControlFill      (tup: (Int   , Int, Int   )) = apply(tup._1, tup._2, tup._3.toFloat)
  implicit def intDoubleControlFill   (tup: (Int   , Int, Double)) = apply(tup._1, tup._2, tup._3.toFloat)
  implicit def stringFloatControlFill (tup: (String, Int, Float )) = apply(tup._1, tup._2, tup._3)
  implicit def stringIntControlFill   (tup: (String, Int, Int   )) = apply(tup._1, tup._2, tup._3.toFloat)
  implicit def stringDoubleControlFill(tup: (String, Int, Double)) = apply(tup._1, tup._2, tup._3.toFloat)
}
final case class ControlFillRange private(key: Any, numChannels: Int, value: Float) {
  private[sciss] def toList: List[Any] = key :: numChannels :: value :: Nil
}