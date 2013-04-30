/**
 * Copyright 2013 amato
 *
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
 */

package it.unich.sci.jandom.domains

/**
 * The base class for all abstract properties.
 * @author Gianluca Amato <gamato@unich.it>
 */
abstract class AbstractProperty[Property] extends PartiallyOrdered[Property] {
  /**
   * The standard widening for two abstract properties.
   * @param that the abstract object to be widened with `this`. `that` is NOT assumed to be bigger than `this`.
   * @note $NOTEDIMENSION
   * @return the widening of the two abstract properties.
   */
  def widening(that: Property): Property

  /**
   * The standard widening for two abstract properties.
   * @param that the abstract object to be narrowed with `this`. `that` IS assumed to be smaller than `this`.
   * @note `that` should be be bigger than `this`.
   * @note $NOTEDIMENSION
   * @return the narrowing of the two abstract properties.
   */
  def narrowing(that: Property): Property


  /**
   * Compute an upper bound of two abstract properties. If it is possible and convenient, this should compute
   * the lowest upper bound, but it is not a requirement.
   * @param that the abstract object to be joined with `this`.
   * @note $NOTEDIMENSION
   * @return an upper bound of the two abstract properties.
   */
  def union(that: Property): Property

  /**
   * Compute a lower bound of two abstract properties. If it is possible and convenient, this should compute
   * the greatest lower bound, but it is not a requirement.
   * @param that the abstract object to meet with `this`.
   * @note $NOTEDIMENSION
   * @return a lower bound of the two abstract properties.
   */
  def intersection(that: Property): Property

}