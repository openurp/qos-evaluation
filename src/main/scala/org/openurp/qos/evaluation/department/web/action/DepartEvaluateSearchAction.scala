/*
 * Copyright (C) 2014, The OpenURP Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openurp.qos.evaluation.department.web.action

import java.time.LocalDate
import org.beangle.commons.collection.Order
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.base.model.{Department, Project, Semester}
import org.openurp.qos.evaluation.department.model.DepartEvaluate
import org.openurp.starter.web.support.ProjectSupport

class DepartEvaluateSearchAction extends RestfulAction[DepartEvaluate] with ProjectSupport {

  override def indexSetting(): Unit = {
    given project: Project = getProject

    put("departments", findInSchool(classOf[Department]))
    put("semesters", entityDao.getAll(classOf[Semester]))
    put("currentSemester", getSemester)
  }

  override def search(): View = {
    // 页面条件
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("departEvaluate.semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val departEvaluates = OqlBuilder.from(classOf[DepartEvaluate], "departEvaluate")
    populateConditions(departEvaluates)
    departEvaluates.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    departEvaluates.where("departEvaluate.semester=:semester", semester)
    put("departEvaluates", entityDao.search(departEvaluates))
    forward()
  }
}
