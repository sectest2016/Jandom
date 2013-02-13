/**
 * Copyright 2013 Gianluca Amato
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

package it.unich.sci.jandom
package targets

import org.scalatest.FunSuite

/**
 * The test suite for Jandom targets.
 * @author Gianluca Amato <amato@sci.unich.it>
 *
 */
class TargetsSuite extends FunSuite {
  override def nestedSuites = List(new EnvironmentSuite, new VariableSuite, new LinearFormSuite, new LinearAssignmentSuite,
      new LinearCondSuite, new SLILProgramSuite, new LTSSuite)
}
