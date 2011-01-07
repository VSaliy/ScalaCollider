/*
 *  SynthGraphBuilder.scala
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

trait SynthGraphBuilder {
   def addLazyGE( g: LazyGE ) : Unit
   def addControlProxy( proxy: ControlProxyLike[ _, _ ]) : Unit
   def build : SynthGraph
}

trait UGenGraphBuilder {
   def addUGen( ugen: UGen ) : Unit
//   def addUGenElem( u: Expands[ UGen ]) : Unit
//   def addControlProxy( proxy: ControlProxyLike[ _, _ ]) : Unit
   def addControl( values: IIdxSeq[ Float ], name: Option[ String ]) : Int
   def build : UGenGraph

   def visit[ U <: AnyRef ]( src: LazyGE, init: => U ) : U

//   private var indivCnt = 0
//   def individuate : Int = {
//      val res = indivCnt
//      indivCnt += 1
//      res
//   }
}
