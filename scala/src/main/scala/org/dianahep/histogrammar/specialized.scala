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

package org.dianahep.histogrammar

import scala.collection.mutable
import scala.language.implicitConversions

import org.dianahep.histogrammar._

/** Specialty methods for familiar combinations of containers, such as histograms. */
package object specialized {
  /** Type alias for conventional histograms (filled). */
  type Histogrammed = Selected[Binned[Counted, Counted, Counted, Counted]]
  /** Type alias for conventional histograms (filling). */
  type Histogramming[DATUM] = Selecting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]
  /** Convenience function for creating a conventional histogram. */
  def Histogram[DATUM]
    (num: Int,
    low: Double,
    high: Double,
    quantity: UserFcn[DATUM, Double],
    selection: UserFcn[DATUM, Double] = unweighted[DATUM]) =
    Select(selection, Bin(num, low, high, quantity))

  /** Type alias for sparsely binned histograms (filled). */
  type SparselyHistogrammed = Selected[SparselyBinned[Counted, Counted]]
  /** Type alias for sparsely binned histograms (filling). */
  type SparselyHistogramming[DATUM] = Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]
  /** Convenience function for creating a sparsely binned histogram. */
  def SparselyHistogram[DATUM]
    (binWidth: Double,
    quantity: UserFcn[DATUM, Double],
    selection: UserFcn[DATUM, Double] = unweighted[DATUM],
    origin: Double = 0.0) =
    Select(selection, SparselyBin(binWidth, quantity, origin = origin))

  /** Type alias for a physicist's "profile plot" (filled). */
  type Profiled = Selected[Binned[Deviated, Counted, Counted, Counted]]
  /** Type alias for a physicist's "profile plot" (filling). */
  type Profiling[DATUM] = Selecting[DATUM, Binning[DATUM, Deviating[DATUM], Counting, Counting, Counting]]
  /** Convenience function for creating a physicist's "profile plot." */
  def Profile[DATUM]
    (num: Int,
    low: Double,
    high: Double,
    binnedQuantity: UserFcn[DATUM, Double],
    averagedQuantity: UserFcn[DATUM, Double],
    selection: UserFcn[DATUM, Double] = unweighted[DATUM]) =
    Select(selection, Bin(num, low, high, binnedQuantity, Deviate(averagedQuantity)))

  /** Type alias for a physicist's sparsely binned "profile plot" (filled). */
  type SparselyProfiled = Selected[SparselyBinned[Deviated, Counted]]
  /** Type alias for a physicist's sparsely binned "profile plot" (filling). */
  type SparselyProfiling[DATUM] = Selecting[DATUM, SparselyBinning[DATUM, Deviating[DATUM], Counting]]
  /** Convenience function for creating a physicist's sparsely binned "profile plot." */
  def SparselyProfile[DATUM]
    (binWidth: Double,
    binnedQuantity: UserFcn[DATUM, Double],
    averagedQuantity: UserFcn[DATUM, Double],
    selection: UserFcn[DATUM, Double] = unweighted[DATUM]) =
    Select(selection, SparselyBin(binWidth, binnedQuantity, Deviate(averagedQuantity)))

  //////////////////////////////////////////////////////////////// conversions to HistogramMethods

  implicit def binnedToHistogramMethods(hist: Binned[Counted, Counted, Counted, Counted]): HistogramMethods =
    new HistogramMethods(new Selected(hist.entries, None, hist))

  implicit def binningToHistogramMethods[DATUM](hist: Binning[DATUM, Counting, Counting, Counting, Counting]): HistogramMethods =
    new HistogramMethods(new Selected(hist.entries, None, Factory.fromJson(hist.toJson).as[Binned[Counted, Counted, Counted, Counted]]))

  implicit def selectedBinnedToHistogramMethods(hist: Selected[Binned[Counted, Counted, Counted, Counted]]): HistogramMethods =
    new HistogramMethods(hist)

  implicit def selectingBinningToHistogramMethods[DATUM](hist: Selecting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]): HistogramMethods =
    new HistogramMethods(Factory.fromJson(hist.toJson).as[Selected[Binned[Counted, Counted, Counted, Counted]]])

  implicit def sparselyBinnedToHistogramMethods(hist: SparselyBinned[Counted, Counted]): HistogramMethods =
    if (hist.numFilled > 0)
      new HistogramMethods(
        new Selected(hist.entries, None, new Binned(hist.low.get, hist.high.get, hist.entries, hist.quantityName, hist.minBin.get to hist.maxBin.get map {i => new Counted(hist.at(i).flatMap(x => Some(x.entries)).getOrElse(0L))}, new Counted(0L), new Counted(0L), hist.nanflow))
      )
    else
      throw new RuntimeException("sparsely binned histogram has no entries")

  implicit def sparselyBinningToHistogramMethods[DATUM](hist: Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]): HistogramMethods =
    sparselyBinnedToHistogramMethods(Factory.fromJson(hist.toJson).as[SparselyBinned[Counted, Counted]])

  implicit def selectedSparselyBinnedToHistogramMethods(hist: Selected[SparselyBinned[Counted, Counted]]): HistogramMethods =
    if (hist.value.numFilled > 0)
      new HistogramMethods(
        new Selected(hist.entries, hist.quantityName, new Binned(hist.value.low.get, hist.value.high.get, hist.value.entries, hist.value.quantityName, hist.value.minBin.get to hist.value.maxBin.get map {i => new Counted(hist.value.at(i).flatMap(x => Some(x.entries)).getOrElse(0L))}, new Counted(0L), new Counted(0L), hist.value.nanflow))
      )
    else
      throw new RuntimeException("sparsely binned histogram has no entries")

  implicit def selectedSparselyBinningToHistogramMethods[DATUM](hist: Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]): HistogramMethods =
    selectedSparselyBinnedToHistogramMethods(Factory.fromJson(hist.toJson).as[Selected[SparselyBinned[Counted, Counted]]])

  //////////////////////////////////////////////////////////////// conversions to ProfileMethods

  implicit def binnedToProfileMethods(hist: Binned[Deviated, Counted, Counted, Counted]): ProfileMethods =
    new ProfileMethods(new Selected(hist.entries, None, hist))

  implicit def binningToProfileMethods[DATUM](hist: Binning[DATUM, Deviating[DATUM], Counting, Counting, Counting]): ProfileMethods =
    new ProfileMethods(new Selected(hist.entries, None, Factory.fromJson(hist.toJson).as[Binned[Deviated, Counted, Counted, Counted]]))

  implicit def selectedBinnedToProfileMethods(hist: Selected[Binned[Deviated, Counted, Counted, Counted]]): ProfileMethods =
    new ProfileMethods(hist)

  implicit def selectingBinningToProfileMethods[DATUM](hist: Selecting[DATUM, Binning[DATUM, Deviating[DATUM], Counting, Counting, Counting]]): ProfileMethods =
    new ProfileMethods(Factory.fromJson(hist.toJson).as[Selected[Binned[Deviated, Counted, Counted, Counted]]])

  implicit def sparselyBinnedToProfileMethods(hist: SparselyBinned[Deviated, Counted]): ProfileMethods =
    if (hist.numFilled > 0)
      new ProfileMethods(
        new Selected(hist.entries, None, new Binned(hist.low.get, hist.high.get, hist.entries, hist.quantityName, hist.minBin.get to hist.maxBin.get map {i => hist.at(i).getOrElse(new Deviated(0.0, None, 0.0, 0.0))}, new Counted(0L), new Counted(0L), hist.nanflow))
      )
    else
      throw new RuntimeException("sparsely binned profile has no entries")

  implicit def sparselyBinningToProfileMethods[DATUM](hist: Selecting[DATUM, SparselyBinning[DATUM, Deviating[DATUM], Counting]]): ProfileMethods =
    sparselyBinnedToProfileMethods(Factory.fromJson(hist.toJson).as[SparselyBinned[Deviated, Counted]])

  implicit def selectedSparselyBinnedToProfileMethods(hist: Selected[SparselyBinned[Deviated, Counted]]): ProfileMethods =
    if (hist.value.numFilled > 0)
      new ProfileMethods(
        new Selected(hist.entries, hist.quantityName, new Binned(hist.value.low.get, hist.value.high.get, hist.value.entries, hist.value.quantityName, hist.value.minBin.get to hist.value.maxBin.get map {i => hist.value.at(i).getOrElse(new Deviated(0.0, None, 0.0, 0.0))}, new Counted(0L), new Counted(0L), hist.value.nanflow))
      )
    else
      throw new RuntimeException("sparsely binned profile has no entries")

  implicit def selectedSparselyBinningToProfileMethods[DATUM](hist: Selecting[DATUM, SparselyBinning[DATUM, Deviating[DATUM], Counting]]): ProfileMethods =
    selectedSparselyBinnedToProfileMethods(Factory.fromJson(hist.toJson).as[Selected[SparselyBinned[Deviated, Counted]]])

}

package specialized {
  /** Methods that are implicitly added to container combinations that look like histograms. */
  class HistogramMethods(hist: Selected[Binned[Counted, Counted, Counted, Counted]]) {
    def binned = hist.value

    /** Bin values as numbers, rather than [[org.dianahep.histogrammar.Counted]]/[[org.dianahep.histogrammar.Counting]]. */
    def numericalValues: Seq[Double] = binned.values.map(_.entries)
    /** Overflow as a number, rather than [[org.dianahep.histogrammar.Counted]]/[[org.dianahep.histogrammar.Counting]]. */
    def numericalOverflow: Double = binned.overflow.entries
    /** Underflow as a number, rather than [[org.dianahep.histogrammar.Counted]]/[[org.dianahep.histogrammar.Counting]]. */
    def numericalUnderflow: Double = binned.underflow.entries
    /** Nanflow as a number, rather than [[org.dianahep.histogrammar.Counted]]/[[org.dianahep.histogrammar.Counting]]. */
    def numericalNanflow: Double = binned.nanflow.entries

    /** ASCII representation of a histogram for debugging on headless systems. Limited to 80 columns. */
    def ascii: String = ascii(80)
    /** ASCII representation of a histogram for debugging on headless systems. Limited to `width` columns. */
    def ascii(width: Int): String = {
      val minCount = Math.min(Math.min(Math.min(binned.values.map(_.entries).min, binned.overflow.entries), binned.underflow.entries), binned.nanflow.entries)
      val maxCount = Math.max(Math.max(Math.max(binned.values.map(_.entries).max, binned.overflow.entries), binned.underflow.entries), binned.nanflow.entries)
      val range = maxCount - minCount
      val minEdge = if (minCount < 0.0) minCount - 0.1*range else 0.0
      val maxEdge = maxCount + 0.1*range

      val binWidth = (binned.high - binned.low) / binned.values.size
      def sigfigs(x: Double, n: Int) = new java.math.BigDecimal(x).round(new java.math.MathContext(n)).toString

      val prefixValues = binned.values.zipWithIndex map {case (v, i) =>
        (i * binWidth + binned.low, (i + 1) * binWidth + binned.low, v.entries)
      }
      val prefixValuesStr = prefixValues map {case (binlow, binhigh, entries) => (sigfigs(Math.abs(binlow), 3), sigfigs(Math.abs(binhigh), 3), sigfigs(Math.abs(entries), 4))}

      val widestBinlow = Math.max(prefixValuesStr.map(_._1.size).max, 2)
      val widestBinhigh = Math.max(prefixValuesStr.map(_._2.size).max, 2)
      val widestValue = Math.max(prefixValuesStr.map(_._3.size).max, 2)
      val formatter = s"[ %s%-${widestBinlow}s, %s%-${widestBinhigh}s) %s%-${widestValue}s "
      val prefixWidth = widestBinlow + widestBinhigh + widestValue + 9

      val reducedWidth = width - prefixWidth
      val zeroIndex = Math.round(reducedWidth * (0.0 - minEdge) / (maxEdge - minEdge)).toInt
      val zeroLine1 = " " * prefixWidth + " " + (if (zeroIndex > 0) " " else "") + " " * zeroIndex + "0" + " " * (reducedWidth - zeroIndex - 10) + " " + f"$maxEdge%10g"
      val zeroLine2 = " " * prefixWidth + " " + (if (zeroIndex > 0) "+" else "") + "-" * zeroIndex + "+" + "-" * (reducedWidth - zeroIndex - 1) + "-" + "+"

      val lines = binned.values zip prefixValues zip prefixValuesStr map {case ((v, (binlow, binhigh, value)), (binlowAbs, binhighAbs, valueAbs)) =>
        val binlowSign = if (binlow < 0) "-" else " "
        val binhighSign = if (binhigh < 0) "-" else " "
        val valueSign = if (value < 0) "-" else " "
        val peakIndex = Math.round(reducedWidth * (v.entries - minEdge) / (maxEdge - minEdge)).toInt
        if (peakIndex < zeroIndex)
          formatter.format(binlowSign, binlowAbs, binhighSign, binhighAbs, valueSign, valueAbs) + (if (zeroIndex > 0) "|" else "") + " " * peakIndex + "*" * (zeroIndex - peakIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          formatter.format(binlowSign, binlowAbs, binhighSign, binhighAbs, valueSign, valueAbs) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (peakIndex - zeroIndex) + " " * (reducedWidth - peakIndex) + "|"
      }

      val underflowIndex = Math.round(reducedWidth * (binned.underflow.entries - minEdge) / (maxEdge - minEdge)).toInt
      val underflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val underflowLine =
        if (underflowIndex < zeroIndex)
          underflowFormatter.format("underflow", sigfigs(binned.underflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * underflowIndex + "*" * (zeroIndex - underflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          underflowFormatter.format("underflow", sigfigs(binned.underflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (underflowIndex - zeroIndex) + " " * (reducedWidth - underflowIndex) + "|"

      val overflowIndex = Math.round(reducedWidth * (binned.overflow.entries - minEdge) / (maxEdge - minEdge)).toInt
      val overflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val overflowLine =
        if (overflowIndex < zeroIndex)
          overflowFormatter.format("overflow", sigfigs(binned.overflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * overflowIndex + "*" * (zeroIndex - overflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          overflowFormatter.format("overflow", sigfigs(binned.overflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (overflowIndex - zeroIndex) + " " * (reducedWidth - overflowIndex) + "|"

      val nanflowIndex = Math.round(reducedWidth * (binned.nanflow.entries - minEdge) / (maxEdge - minEdge)).toInt
      val nanflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val nanflowLine =
        if (nanflowIndex < zeroIndex)
          nanflowFormatter.format("nanflow", sigfigs(binned.nanflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * nanflowIndex + "*" * (zeroIndex - nanflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          nanflowFormatter.format("nanflow", sigfigs(binned.nanflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (nanflowIndex - zeroIndex) + " " * (reducedWidth - nanflowIndex) + "|"

      (List(zeroLine1, zeroLine2, underflowLine) ++ lines ++ List(overflowLine, nanflowLine, zeroLine2)).mkString("\n")      
    }
  }

  /** Methods that are implicitly added to container combinations that look like a physicist's "profile plot." */
  class ProfileMethods(prof: Selected[Binned[Deviated, Counted, Counted, Counted]]) {
  }

}
