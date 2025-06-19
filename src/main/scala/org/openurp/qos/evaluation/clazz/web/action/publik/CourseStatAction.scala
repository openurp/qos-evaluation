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

package org.openurp.qos.evaluation.clazz.web.action.publik

import org.beangle.data.dao.{EntityDao, OqlBuilder}
import org.beangle.webmvc.support.{ActionSupport, ParamSupport}
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.EntityAction
import org.openurp.base.model.{Project, Semester}
import org.openurp.code.edu.model.CourseCategory
import org.openurp.qos.evaluation.base.model.AssessGrade
import org.openurp.qos.evaluation.clazz.model.CourseEvalStat
import org.openurp.starter.web.support.ProjectSupport

class CourseStatAction extends ActionSupport with EntityAction[CourseEvalStat] with ParamSupport with ProjectSupport {

  var entityDao: EntityDao = _

  def index(): View = {
    given project: Project = getProject

    put("project", project)
    put("grades", entityDao.getAll(classOf[AssessGrade]))
    put("categories", getCodes(classOf[CourseCategory]))
    put("currentSemester", getSemester)
    forward()
  }

  def search(): View = {
    val builder = super.getQueryBuilder
    builder.where("courseEvalStat.publishStatus=2")
    put("courseEvalStats", entityDao.search(builder))
    forward()
  }

}
