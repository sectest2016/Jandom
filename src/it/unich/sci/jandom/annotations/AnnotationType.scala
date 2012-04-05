/**
 * This file is part of JANDOM: JVM-based Analyzer for Numerical DOMains
 * JANDOM is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JANDOM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty ofa
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JANDOM.  If not, see <http://www.gnu.org/licenses/>.
 *
 * (c) 2012 Gianluca Amato
 */
package it.unich.sci.jandom.annotations

import it.unich.sci.jandom.targets.Target

/**
 * These are classes used for typing annotations in blackboards and specifying
 * default values.
 * @author Gianluca Amato <g.amato@unich.it>
 *
 */
abstract class AnnotationType {
  /**
   * The type of the annotation
   */
  type T
  
  /** 
   * The default value for annotations.
   */
  val defaultValue: T
}