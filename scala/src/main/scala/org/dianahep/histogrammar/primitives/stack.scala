// Copyright 2016 Jim Pivarski
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
//     http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.dianahep

import scala.collection.immutable.SortedSet

import org.dianahep.histogrammar.json._

package histogrammar {
  //////////////////////////////////////////////////////////////// Stack/Stacked/Stacking

  /** Accumulate a suite containers, filling all that are above a given cut on a given expression.
    * 
    * Factory produces mutable [[org.dianahep.histogrammar.Stacking]] and immutable [[org.dianahep.histogrammar.Stacked]] objects.
    */
  object Stack extends Factory {
    val name = "Stack"
    val help = "Accumulate a suite containers, filling all that are above a given cut on a given expression."
    val detailedHelp = """Stack(value: => V, expression: NumericalFcn[DATUM], cuts: Double*)"""

    /** Create an immutable [[org.dianahep.histogrammar.Stacked]] from arguments (instead of JSON).
      * 
      * @param entries weighted number of entries (sum of all observed weights)
      * @param cuts lower thresholds and their associated containers, starting with negative infinity
      */
    def ed[V <: Container[V]](entries: Double, cuts: (Double, V)*) = new Stacked(entries, cuts: _*)

    /** Create an empty, mutable [[org.dianahep.histogrammar.Stacking]].
      * 
      * @param value new value (note the `=>`: expression is reevaluated every time a new value is needed)
      * @param expression numerical expression whose value is compared with the given thresholds
      * @param cuts thresholds that will be used to determine which datum goes into a given container; this list gets sorted, duplicates get removed, and negative infinity gets added as the first element
      */
    def apply[DATUM, V <: Container[V] with Aggregation{type Datum >: DATUM}](value: => V, expression: NumericalFcn[DATUM], cuts: Double*) =
      new Stacking(expression, 0.0, (java.lang.Double.NEGATIVE_INFINITY +: SortedSet(cuts: _*).toList).map((_, value)): _*)

    def unapply[V <: Container[V]](x: Stacked[V]) = Some((x.entries, x.cuts))
    def unapply[DATUM, V <: Container[V] with Aggregation{type Datum >: DATUM}](x: Stacking[DATUM, V]) = Some((x.entries, x.cuts))

    def fromJsonFragment(json: Json): Container[_] = json match {
      case JsonObject(pairs @ _*) if (pairs.keySet == Set("entries", "type", "data")) =>
        val get = pairs.toMap

        val entries = get("entries") match {
          case JsonNumber(x) => x
          case x => throw new JsonFormatException(x, name + ".entries")
        }

        val factory = get("type") match {
          case JsonString(name) => Factory(name)
          case x => throw new JsonFormatException(x, name + ".type")
        }

        get("data") match {
          case JsonArray(elements @ _*) if (elements.size >= 1) =>
            new Stacked[Container[_]](entries, elements.zipWithIndex map {case (element, i) =>
              element match {
                case JsonObject(elementPairs @ _*) if (elementPairs.keySet == Set("atleast", "data")) =>
                  val elementGet = elementPairs.toMap
                  val atleast = elementGet("atleast") match {
                    case JsonNumber(x) => x
                    case x => throw new JsonFormatException(x, name + s".data element $i atleast")
                  }
                  (atleast, factory.fromJsonFragment(elementGet("data")))

                case x => throw new JsonFormatException(x, name + s".data element $i")
              }
            }: _*)

          case x => throw new JsonFormatException(x, name + ".data")
        }

      case _ => throw new JsonFormatException(json, name)
    }
  }

  /** An accumulated suite of containers, each collecting data above a given cut on a given expression.
    * 
    * @param entries weighted number of entries (sum of all weights)
    * @param cuts lower thresholds and their associated containers, starting with negative infinity
    */
  class Stacked[V <: Container[V]](val entries: Double, val cuts: (Double, V)*) extends Container[Stacked[V]] {
    type Type = Stacked[V]
    def factory = Stack

    if (entries < 0.0)
      throw new ContainerException(s"entries ($entries) cannot be negative")
    if (cuts.size < 1)
      throw new ContainerException(s"number of cuts (${cuts.size}) must be at least 1 (including the implicit >= -inf, which the Stack.ing factory method adds)")

    def zero = new Stacked[V](0.0, cuts map {case (c, v) => (c, v.zero)}: _*)
    def +(that: Stacked[V]) =
      if (this.cuts.size != that.cuts.size)
        throw new ContainerException(s"cannot add Stacked because the number of cut differs (${this.cuts.size} vs ${that.cuts.size})")
      else
        new Stacked(
          this.entries + that.entries,
          this.cuts zip that.cuts map {case ((mycut, me), (yourcut, you)) =>
            if (mycut != yourcut)
              throw new ContainerException(s"cannot add Stacked because cut differs ($mycut vs $yourcut)")
            (mycut, me + you)
          }: _*)

    def toJsonFragment = JsonObject(
      "entries" -> JsonFloat(entries),
      "type" -> JsonString(cuts.head._2.factory.name),
      "data" -> JsonArray(cuts map {case (atleast, sub) => JsonObject("atleast" -> JsonFloat(atleast), "data" -> sub.toJsonFragment)}: _*))

    override def toString() = s"""Stacked[entries=$entries, ${cuts.head._2}, cuts=[${cuts.map(_._1).mkString(", ")}]]"""
    override def equals(that: Any) = that match {
      case that: Stacked[V] => this.entries === that.entries  &&  (this.cuts zip that.cuts forall {case (me, you) => me._1 === you._1  &&  me._2 == you._2})
      case _ => false
    }
    override def hashCode() = (entries, cuts).hashCode()
  }

  /** Accumulating a suite of containers, each collecting data above a given cut on a given expression.
    * 
    * @param expression numerical expression whose value is compared with the given thresholds
    * @param entries weighted number of entries (sum of all observed weights)
    * @param cuts lower thresholds and their associated containers, starting with negative infinity
    */
  class Stacking[DATUM, V <: Container[V] with Aggregation{type Datum >: DATUM}](val expression: NumericalFcn[DATUM], var entries: Double, val cuts: (Double, V)*) extends Container[Stacking[DATUM, V]] with AggregationOnData {
    type Type = Stacking[DATUM, V]
    type Datum = DATUM
    def factory = Stack

    if (entries < 0.0)
      throw new ContainerException(s"entries ($entries) cannot be negative")
    if (cuts.size < 1)
      throw new ContainerException(s"number of cuts (${cuts.size}) must be at least 1 (including the implicit >= -inf, which the Stack.ing factory method adds)")

    def zero = new Stacking[DATUM, V](expression, 0.0, cuts map {case (c, v) => (c, v.zero)}: _*)
    def +(that: Stacking[DATUM, V]) =
      if (this.cuts.size != that.cuts.size)
        throw new ContainerException(s"cannot add Stacking because the number of cut differs (${this.cuts.size} vs ${that.cuts.size})")
      else
        new Stacking(
          this.expression,
          this.entries + that.entries,
          this.cuts zip that.cuts map {case ((mycut, me), (yourcut, you)) =>
            if (mycut != yourcut)
              throw new ContainerException(s"cannot add Stacking because cut differs ($mycut vs $yourcut)")
            (mycut, me + you)
          }: _*)

    def fillWeighted[SUB <: Datum](datum: SUB, weight: Double) {
      if (weight > 0.0) {
        val value = expression(datum)
        entries += weight
        cuts foreach {case (threshold, sub) =>
          if (value >= threshold)
            sub.fillWeighted(datum, weight)
        }
      }
    }

    def toJsonFragment = JsonObject(
      "entries" -> JsonFloat(entries),
      "type" -> JsonString(cuts.head._2.factory.name),
      "data" -> JsonArray(cuts map {case (atleast, sub) => JsonObject("atleast" -> JsonFloat(atleast), "data" -> sub.toJsonFragment)}: _*))

    override def toString() = s"""Stacking[entries=$entries, ${cuts.head._2}, cuts=[${cuts.map(_._1).mkString(", ")}]]"""
    override def equals(that: Any) = that match {
      case that: Stacking[DATUM, V] => this.expression == that.expression  &&  this.entries === that.entries  &&  (this.cuts zip that.cuts forall {case (me, you) => me._1 === you._1  &&  me._2 == you._2})
      case _ => false
    }
    override def hashCode() = (expression, entries, cuts).hashCode()
  }
}
