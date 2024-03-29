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

import org.beangle.data.dao.OqlBuilder
import org.beangle.data.model.Entity
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.base.model.{Project, Semester}
import org.openurp.base.service.SemesterService
import org.openurp.starter.edu.helper.ProjectSupport

import java.time.LocalDate

abstract class ProjectRestfulAction[T <: Entity[_]] extends RestfulAction[T] with ProjectSupport {

  var semesterService: SemesterService = _

  def get(project: Project, date: LocalDate): Semester = {
    val builder = OqlBuilder.from(classOf[Semester], "semester")
      .where("semester.calendar = :calendar", project.calendar)
    builder.where(":date between semester.beginOn and  semester.endOn", LocalDate.now)
    builder.cacheable()
    val rs = entityDao.search(builder)
    if (rs.isEmpty) {
      val builder2 = OqlBuilder.from(classOf[Semester], "semester")
        .where("semester.calendar = :calendar", project.calendar)
      builder2.orderBy("abs(semester.beginOn - current_date() + semester.endOn - current_date())")
      builder2.cacheable()
      builder2.limit(1, 1)
      val rs2 = entityDao.search(builder2)
      if (rs2.nonEmpty) {
        rs2.head
      } else {
        null
      }
    } else {
      rs.head
    }
  }
}
