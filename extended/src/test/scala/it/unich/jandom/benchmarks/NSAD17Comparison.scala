/**
  * Copyright 2017 Gianluca Amato <gianluca.amato@unich.it>
  *
  * This file is part of JANDOM: JVM-based Analyzer for Numerical DOMains
  * JANDOM is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * JANDOM is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of a
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with JANDOM.  If not, see <http://www.gnu.org/licenses/>.
  */

package it.unich.jandom.benchmarks

import it.unich.jandom.domains.numerical._
import it.unich.jandom.domains.numerical.ppl._
import it.unich.jandom.targets.lts.{LTS, Location}
import it.unich.jandom.targets.parameters.NarrowingSpecs._
import it.unich.jandom.targets.parameters.WideningScope
import it.unich.jandom.targets.parameters.WideningSpecs._
import it.unich.jandom.targets.{EQSSolver, Parameters}
import it.unich.jandom.utils.numberext.RationalExt
import it.unich.scalafix.{Assignment, FixpointSolverListener}
import parma_polyhedra_library._
import spire.math.Rational

import scala.collection.mutable.Map

/**
  * Benchmark program used for the NSAD 2017 paper.
  */
object NSAD17Comparison extends App with FASTLoader {
  val box = BoxRationalDomain()
  val octagons = PPLDomainMacro[Octagonal_Shape_double]
  val polyhedra = PPLDomainMacro[C_Polyhedron]
  val ptope = ParallelotopeRationalDomain(favorAxis = 1)
  val ptopeproduct = new ProductDomain(BoxRationalDomain(), ParallelotopeRationalDomain(favorAxis = -1))

  type Config = (String, NumericalDomain, WideningSpec)

  type Cell = (Int, Int, Config)

  type CellConstraint = (LTS, Location, Seq[Rational])

  val configs: Seq[Config] = Seq(
    ("box", box, DefaultWidening),
    ("octagons", octagons, DefaultWidening),
    ("parallelotope", ptope, DefaultWidening),
    ("ptope-boxes", ptopeproduct, DefaultWidening),
    ("polyhedra H79", polyhedra, NamedWidening("H79")),
    ("polyhedra BHRZ03", polyhedra, NamedWidening("BHRZ03"))
  )

  val wideningDelays = Seq(0, 1, 2, 3, 4, 5, 6)

  val narrowingDelays = Seq(0, 1, 2, 3)

  val cells = for (nd <- narrowingDelays; wd <- wideningDelays; c <- configs) yield  (nd, wd, c)

  val results = Map.empty[(Cell, LTS), Assignment[Location, NumericalProperty[_]]]

  val times = Map.empty[(Cell, LTS), Long]

  val bounds = Map.empty[Cell, Map[CellConstraint, RationalExt]]


  def CStoPolyehdra(dimension: Int, c: Seq[LinearForm]) = {
    c.foldLeft(polyhedra.top(dimension)) { (p: polyhedra.Property, lf: LinearForm) => p.linearInequality(lf) }
  }

  def warmupJVM(): Unit = {
    println("Warmup JVM")
    for (config <- configs; lts <- ltss) {
      val (namedom, dom, wideningSpec) = config
      val params = new Parameters[LTS] {
        val domain: dom.type = dom
      }
      params.widening = DelayedWidening(wideningSpec, 1)
      params.narrowing = DelayedNarrowing(DefaultNarrowing, 1)
      val rho = EQSSolver(lts)(dom)(params)(FixpointSolverListener.EmptyListener)
    }
  }

  def computeAnalysis(): Unit = {
    println("Computing analysis")
    val size = cells.size
    for {
      (cell,i) <- cells.zipWithIndex
      (narrowingDelay, wideningDelay, (namedom, dom, wideningSpec)) = cell
      lts <- ltss
    } {
      val percentage = i * 100 / size
      print(s"\r$percentage%")
      val params = new Parameters[LTS] {
        val domain: dom.type = dom
      }
      params.widening = DelayedWidening(wideningSpec, wideningDelay)
      params.narrowing = DelayedNarrowing(DefaultNarrowing, narrowingDelay)
      val t = System.currentTimeMillis()
      val rho = EQSSolver(lts)(dom)(params)(FixpointSolverListener.EmptyListener)
      val elapsed = System.currentTimeMillis() - t
      results += (cell, lts) -> rho
      times += (cell, lts) -> elapsed
    }
    println
  }

  def computeIntervalBounds(): Unit = {
    println("Computing interval bounds")
    for {
      ((cell, lts), result) <- results
      l <- lts.locations
      v <- 0 until lts.env.size
      iv <- Seq(-Rational.one, Rational.one)
    } {
      val lf = LinearForm.sparse(Rational.zero, (v, iv))
      // val res = result(l)
      // alternative version which is more precise for ptope+boxes
      val res: NumericalProperty[_] = if (cell._3._1 == "ptope-boxes")
        CStoPolyehdra(result(l).dimension, result(l).constraints)
      else
        result(l)
      val upperbound = res.maximize(lf)
      val innermap = bounds.getOrElseUpdate(cell, Map.empty)
      innermap += (lts, l, lf.coeffs) -> upperbound
    }
  }

  def reportAnalysis(name: String, reportCell: Cell => Unit): Unit = {
    println(s"Reporting Analysis: $name")
    for (narrowingDelay <- narrowingDelays) {
      println(s"narrowing delay $narrowingDelay")
      for (config <- configs) {
        val (namedom,_,_) = config
        print(f"$namedom%17s")
        for (wideningDelay <- wideningDelays)
          reportCell((narrowingDelay, wideningDelay, config))
        println
      }
      println
    }
  }

  def reportCellTime(cell: Cell): Unit = {
    var time: Long = 0
    for (lts <- ltss) time += times((cell, lts))
    print(f"$time%6s")
  }

  def reportCellFiniteBounds(cell: Cell): Unit = {
    var finiteBounds: Int = 0
    for ((_, bound) <- bounds(cell))
      if (!bound.isPosInfinity) finiteBounds += 1
    print(f"$finiteBounds%4s")
  }

  def reportFiniteBoundsOctagon(): Unit = {
    for (narrowingDelay <- narrowingDelays) {
      println(s"narrowing delay ${
        narrowingDelay
      }")
      for ((namedom, dom, wideningSpec) <- configs) {
        print(f"$namedom%17s")
        for (wideningDelay <- wideningDelays) {
          var finite_bounds: Int = 0
          for (lts <- ltss) {
            val params = new Parameters[LTS] {
              val domain: dom.type = dom
            }
            params.widening = DelayedWidening(wideningSpec, wideningDelay)
            params.narrowing = DelayedNarrowing(DefaultNarrowing, narrowingDelay)
            val result = EQSSolver(lts)(dom)(params)(FixpointSolverListener.EmptyListener)
            for (l <- lts.locations;
                 v <- 0 until lts.env.size) {
              val res = result(l)
              // alternative versione which is more precise for ptope+boxes
              // val rboxscope = scopees = CStoPolyehdra(result(l).dimension,result(l).constraints)
              val upper_bound = res.maximize(LinearForm.v(v))
              if (!upper_bound.isPosInfinity) finite_bounds += 1
              val lower_bound = res.maximize(-LinearForm.v(v))
              if (!lower_bound.isNegInfinity) finite_bounds += 1
              for (iv <- Seq(-1, 1);
                   w <- 0 until v;
                   iw <- Seq(-1, 1)) {
                val upper_bound = res.maximize(LinearForm.sparse(Rational.zero, (w, iw), (v, iv)))
                if (!upper_bound.isPosInfinity) finite_bounds += 1
              }
            }
          }
          print(f"${
            finite_bounds
          }%5s")
        }
        println
      }
      println
    }
  }


  def reportBestFiniteBounds(): Unit = {
    for (narrowingDelay <- narrowingDelays) {
      val upper_bounds = collection.mutable.HashMap.empty[(Int, String, LTS, Location, Int), RationalExt]
      val lower_bounds = collection.mutable.HashMap.empty[(Int, String, LTS, Location, Int), RationalExt]
      println(s"narrowing delay ${
        narrowingDelay
      }")
      for ((namedom, dom, wideningSpec) <- configs) {
        for (wideningDelay <- wideningDelays) {
          for (lts <- ltss) {
            val params = new Parameters[LTS] {
              val domain: dom.type = dom
            }
            params.widening = DelayedWidening(wideningSpec, wideningDelay)
            params.narrowing = DelayedNarrowing(DefaultNarrowing, narrowingDelay)
            val result = EQSSolver(lts)(dom)(params)(FixpointSolverListener.EmptyListener)
            for (l <- lts.locations;
                 v <- 0 until lts.env.size) {
              val lfv = LinearForm.v(v)
              upper_bounds += (wideningDelay, namedom, lts, l, v) -> result(l).maximize(lfv)
              lower_bounds += (wideningDelay, namedom, lts, l, v) -> result(l).minimize(lfv)
            }
          }
        }
      }

      for ((namedom, dom, wideningSpec) <- configs) {
        print(f"$namedom%17s")
        for (wideningDelay <- wideningDelays) {
          var total_best: Int = 0
          for (lts <- ltss;
               l <- lts.locations;
               v <- 0 until lts.env.size) {
            val upperBound = upper_bounds((wideningDelay, namedom, lts, l, v))
            val upperBoundBest = !upperBound.isPosInfinity && (configs.forall {
              domainSpec =>
                upperBound <= upper_bounds((wideningDelay, domainSpec._1, lts, l, v))
            })
            if (upperBoundBest) total_best += 1
            val lowerBound = lower_bounds((wideningDelay, namedom, lts, l, v))
            val lowerBoundBest = !lowerBound.isNegInfinity && (configs.forall {
              domainSpec =>
                lowerBound >= lower_bounds((wideningDelay, domainSpec._1, lts, l, v))
            })
            if (lowerBoundBest) total_best += 1
          }
          print(f"${
            total_best
          }%4s")
        }
        println
      }
      println
    }
  }

  def reportBestFiniteBoundsOctagon(): Unit = {
    for (narrowingDelay <- narrowingDelays) {
      val bounds = collection.mutable.HashMap.empty[(Int, String, LTS, Location, Seq[Rational]), RationalExt]
      println(s"narrowing delay ${
        narrowingDelay
      }")
      for ((namedom, dom, wideningSpec) <- configs) {
        for (wideningDelay <- wideningDelays) {
          for (lts <- ltss) {
            val params = new Parameters[LTS] {
              val domain: dom.type = dom
            }
            params.widening = DelayedWidening(wideningSpec, wideningDelay)
            params.narrowing = DelayedNarrowing(DefaultNarrowing, narrowingDelay)
            val result = EQSSolver(lts)(dom)(params)(FixpointSolverListener.EmptyListener)
            for (l <- lts.locations;
                 v <- 0 until lts.env.size) {
              val lfv = LinearForm.v(v)
              bounds += (wideningDelay, namedom, lts, l, lfv.coeffs) -> result(l).maximize(lfv)
              val lfmv = -LinearForm.v(v)
              bounds += (wideningDelay, namedom, lts, l, lfmv.coeffs) -> result(l).maximize(lfmv)
              for (iv <- Seq(-1, 1);
                   w <- 0 until v;
                   iw <- Seq(-1, 1)) {
                val lf = LinearForm.sparse(Rational.zero, (w, iw), (v, iv))
                bounds += (wideningDelay, namedom, lts, l, lf.coeffs) -> result(l).maximize(lf)
              }
            }
          }
        }
      }

      for ((namedom, dom, wideningSpec) <- configs) {
        print(f"$namedom%17s")
        for (wideningDelay <- wideningDelays) {
          var total_best: Int = 0
          for (lts <- ltss;
               l <- lts.locations;
               v <- 0 until lts.env.size) {
            val lfv = LinearForm.v(v)
            val bound1 = bounds((wideningDelay, namedom, lts, l, lfv.coeffs))
            if (!bound1.isPosInfinity && (configs.forall {
              domainSpec =>
                bound1 <= bounds((wideningDelay, domainSpec._1, lts, l, lfv.coeffs))
            })) total_best += 1
            val lfmv = -LinearForm.v(v)
            val bound2 = bounds((wideningDelay, namedom, lts, l, lfmv.coeffs))
            if (!bound2.isPosInfinity && (configs.forall {
              domainSpec =>
                bound2 <= bounds((wideningDelay, domainSpec._1, lts, l, lfmv.coeffs))
            })) total_best += 1
            for (iv <- Seq(-1, 1);
                 w <- 0 until v;
                 iw <- Seq(-1, 1)) {
              val lf = LinearForm.sparse(Rational.zero, (w, iw), (v, iv))
              val bound = bounds((wideningDelay, namedom, lts, l, lf.coeffs))
              if (!bound.isPosInfinity && (configs.forall {
                domainSpec =>
                  bound <= bounds((wideningDelay, domainSpec._1, lts, l, lf.coeffs))
              })) total_best += 1
            }
          }
          print(f"${
            total_best
          }%5s")
        }
        println
      }
      println
    }
  }

  def reportBoundImprovements(): Unit = {
    for (narrowingDelay <- narrowingDelays) {
      val upper_bounds = collection.mutable.HashMap.empty[(Int, String, LTS, Location, Int), RationalExt]
      val lower_bounds = collection.mutable.HashMap.empty[(Int, String, LTS, Location, Int), RationalExt]
      println(s"narrowing delay ${
        narrowingDelay
      }")
      for ((namedom, dom, wideningSpec) <- configs) {
        for (wideningDelay <- wideningDelays) {
          for (lts <- ltss) {
            val params = new Parameters[LTS] {
              val domain: dom.type = dom
            }
            params.widening = DelayedWidening(wideningSpec, wideningDelay)
            params.narrowing = DelayedNarrowing(DefaultNarrowing, narrowingDelay)
            val result = EQSSolver(lts)(dom)(params)(FixpointSolverListener.EmptyListener)
            for (l <- lts.locations;
                 v <- 0 until lts.env.size) {
              val lfv = LinearForm.v(v)
              // val res = result(l)
              // alternative versione which is more precise for ptope+boxes
              val res = CStoPolyehdra(result(l).dimension, result(l).constraints)
              upper_bounds += (wideningDelay, namedom, lts, l, v) -> res.maximize(lfv)
              lower_bounds += (wideningDelay, namedom, lts, l, v) -> res.minimize(lfv)
            }
          }
        }
      }

      for ((namecompdom, _, _) <- configs) {
        println(s"Comparison vith $namecompdom")
        for ((namedom, _, _) <- configs) {
          print(f"$namedom%17s")
          for (wideningDelay <- wideningDelays) {
            var total_better: Int = 0
            var total_worse: Int = 0
            for (lts <- ltss;
                 l <- lts.locations;
                 v <- 0 until lts.env.size) {
              val upperBound = upper_bounds((wideningDelay, namedom, lts, l, v))
              val upperBoundComp = upper_bounds((wideningDelay, namecompdom, lts, l, v))
              if (upperBound < upperBoundComp) total_better += 1
              if (upperBound > upperBoundComp) total_worse += 1
              val lowerBound = lower_bounds((wideningDelay, namedom, lts, l, v))
              val lowerBoundComp = lower_bounds((wideningDelay, namecompdom, lts, l, v))
              if (lowerBound > lowerBoundComp) total_better += 1
              if (lowerBound < lowerBoundComp) total_worse += 1
            }
            print(f"${
              "+" + total_better + "/-" + total_worse
            }%10s")
          }
          println
        }
        println
      }
      println
    }
  }

  def timeAnalysisPrecisely(): Unit = {

    val numtries = 5
    val timesArray = new Array[scala.collection.immutable.Map[(Cell, LTS), Long]](numtries)

    warmupJVM()
    for (i <- 0 until numtries) {
      println(s"Analysis number $i")
      computeAnalysis()
      timesArray(i) = times.toMap
      times.empty
    }
    reportAnalysis("time", reportCellTimeMean _)

    def reportCellTimeMean(cell: Cell): Unit = {
      var completeTimeArray = new Array[Long](numtries)
      var squareSum: Long = 0
      for (i <- 0 until numtries; lts <- ltss)
        completeTimeArray(i) += timesArray(i)((cell, lts))
      val meantime = completeTimeArray.sum / numtries
      for (i <- 0 until numtries) {
        val diff = completeTimeArray(i) - meantime
        squareSum += diff*diff
      }
      val stddev = Math.sqrt(squareSum * 1.0 / (numtries - 1)).toLong
      print(f"$meantime%6s \u00B1 $stddev%-3s")
    }
  }

  //timeAnalysisPrecisely()
  //computeAnalysis()
  //computeIntervalBounds()
  //reportAnalysis("finite bounds",reportCellFiniteBounds _)

  reportBestFiniteBoundsOctagon()
}