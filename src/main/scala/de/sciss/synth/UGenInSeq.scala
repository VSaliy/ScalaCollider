/*
 *  UGenInSeq.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2011 Hanns Holger Rutz. All rights reserved.
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
 *
 *
 *  Changelog:
 */

package de.sciss.synth

import collection.immutable.{ IndexedSeq => IIdxSeq }

/**
 *    A collection of UGenIn objects, wrapped as a graph element.
 *    This is mainly used in multi-channel expansion.
 */
//case class UGenInSeq( outputs: IIdxSeq[ UGenIn ]) extends GE {
//   override def toString = outputs.mkString( "[", ", ", "]" )
//
////   override private[synth] def ops = new UGenInSeqOps( this )
//}

case class UGenInSeq( elems: IIdxSeq[ AnyUGenIn ]) extends /* IIdxSeq[ U ] with */ MultiGE { //
//   def expand = this
   def expand = elems // this
//   def apply( idx: Int ) = elems( idx )
//   def length : Int = elems.length
}

case class RatedUGenInSeq[ R <: Rate, +U <: UGenIn[ R ]]( rate: R, elems: IIdxSeq[ U ]) extends /* IIdxSeq[ U ] with */ GE[ R, U ] { //
//   def expand = this
   def expand = elems // this
//   def apply( idx: Int ) = elems( idx )
//   def length : Int = elems.length
}

object GESeq {
   def apply[ R <: Rate, U <: UGenIn[ R ]]( elems: U* )( implicit rate: R ) = new GESeq[ R, U ]( elems: _* )( rate )
}
class GESeq[ R <: Rate, +U <: UGenIn[ R ]] private ( elems: U* )( implicit val rate: R ) extends IIdxSeq[ U ] with GE[ R, U ] {
   def expand = this
   def apply( idx: Int ) = elems( idx )
   def length : Int = elems.length
}