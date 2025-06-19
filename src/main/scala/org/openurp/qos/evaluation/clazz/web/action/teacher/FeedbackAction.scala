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

package org.openurp.qos.evaluation.clazz.web.action.teacher

import org.beangle.data.dao.{EntityDao, OqlBuilder}
import org.beangle.security.Securities
import org.beangle.webmvc.support.ActionSupport
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.EntityAction
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Project, Semester}
import org.openurp.qos.evaluation.clazz.model.Feedback
import org.openurp.qos.evaluation.clazz.web.helper.ClazzFeedback
import org.openurp.starter.web.support.{ProjectSupport, TeacherSupport}

import java.time.LocalDate

class FeedbackAction extends TeacherSupport {

  protected override def projectIndex(me: Teacher)(using project: Project): View = {
    val semester = getSemester
    put("currentSemester", semester)
    val fbQuery = OqlBuilder.from(classOf[Feedback], "fb")
    fbQuery.where("fb.semester=:semester", semester)
    fbQuery.where("fb.teacher=:teacher", me)
    val fds = entityDao.search(fbQuery)
    val feedbacks = fds.groupBy(_.crn).map { case (k, l) =>
      new ClazzFeedback(k, l.head.course, l.sortBy(_.updatedAt).reverse)
    }
    put("stdCount", fds.map(_.std).distinct.size)
    put("feedbacks", feedbacks)
    forward()
  }

}
