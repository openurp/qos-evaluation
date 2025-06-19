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

import org.beangle.commons.collection.{Collections, Order}
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.annotation.{mapping, param}
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.base.model.{Project, Semester}
import org.openurp.qos.evaluation.clazz.model.CourseEvalStat
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction
import org.openurp.qos.evaluation.base.model.Option

import java.time.LocalDate

class CourseEvalSearchAction extends ProjectRestfulAction[CourseEvalStat] {

  override def index(): View = {
    given project: Project = getProject

    put("currentSemester", getSemester)
    forward()
  }

  override def search(): View = {
    val courseEvalStat = OqlBuilder.from(classOf[CourseEvalStat], "courseEvalStat")
    populateConditions(courseEvalStat)
    courseEvalStat.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    put("courseEvalStats", entityDao.search(courseEvalStat))
    forward()
  }

  @mapping(value = "{id}")
  override def info(@param("id") id: String): View = {
    val questionnaireStat = entityDao.get(classOf[CourseEvalStat], java.lang.Long.parseLong(id))
    put("questionnaireStat", questionnaireStat)

    val list = Collections.newBuffer[Option]
    val questions = questionnaireStat.questions
    questions foreach { question =>
      val options = question.optionGroup.options
      options foreach { option =>
        var tt = 0
        list foreach { oldOption =>
          if (oldOption.id == option.id) {
            tt += 1
          }
        }
        if (tt == 0) {
          list += option
        }
      }
    }
    put("options", list)
    put("questionnaireStat", questionnaireStat)
    forward()
  }

}
