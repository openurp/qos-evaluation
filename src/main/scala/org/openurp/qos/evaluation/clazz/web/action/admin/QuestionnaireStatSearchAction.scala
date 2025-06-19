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

package org.openurp.qos.evaluation.clazz.web.action.admin

import org.beangle.commons.collection.Collections
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.base.model.{Department, Project, Semester}
import org.openurp.qos.evaluation.clazz.model.CourseEvalStat
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction
import org.openurp.qos.evaluation.base.model.{AssessCriteria, Indicator, Questionnaire}

import java.time.LocalDate

class QuestionnaireStatSearchAction extends ProjectRestfulAction[CourseEvalStat] {

  override def index(): View = {
    given project: Project = getProject

    put("stdTypeList", project.stdTypes)
    put("departmentList", project.departments)

    var searchFormFlag = get("searchFormFlag").orNull
    if (searchFormFlag == null) {
      searchFormFlag = "beenStat"
    }
    put("searchFormFlag", searchFormFlag)
    put("departments", entityDao.getAll(classOf[Department]))
    val query = OqlBuilder.from(classOf[Questionnaire], "questionnaire")
    put("questionnaires", entityDao.search(query))
    put("currentSemester",getSemester)
    put("assessCriterias", entityDao.getAll(classOf[AssessCriteria]))
    put("indicators", entityDao.getAll(classOf[Indicator]))
    forward()
  }

  protected def getOptionMap(): collection.Map[String, Float] = {
    val optionNameMap = Collections.newMap[String, Float]
    optionNameMap.put("A", 90.toFloat)
    optionNameMap.put("B", 80.toFloat)
    optionNameMap.put("C", 60.toFloat)
    optionNameMap.put("D", 0.toFloat)
    optionNameMap
  }

}
