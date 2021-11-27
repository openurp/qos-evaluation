/*
 * Copyright (C) 2005, The OpenURP Software.
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

import org.beangle.data.dao.OqlBuilder
import org.beangle.security.Securities
import org.beangle.web.action.annotation.{mapping, param}
import org.beangle.web.action.view.{Status, View}
import org.beangle.webmvc.support.action.EntityAction
import org.openurp.base.edu.model.{Semester, Teacher}
import org.openurp.qos.evaluation.clazz.model.*
import org.openurp.starter.edu.helper.ProjectSupport

class CourseAction extends EntityAction[CourseEvalStat] with ProjectSupport {

  def index(): View = {
    val me = getTeacher()
    put("project", me.projects.head)
    val semester = getId("semester") match {
      case Some(sid) => entityDao.get(classOf[Semester], sid.toInt)
      case None =>
        val query = OqlBuilder.from[Semester](classOf[CourseEvalStat].getName, "stat")
        query.where("stat.teacher=:me", me)
        query.select("stat.semester")
        query.where("stat.publishStatus>=1")
        query.orderBy("stat.semester.beginOn desc")
        val semesters = entityDao.search(query)
        if (semesters.isEmpty) {
          getCurrentSemester
        } else {
          semesters.head
        }
    }
    put("currentSemester", semester)
    val query = OqlBuilder.from(classOf[CourseEvalStat], "stat")
    query.where("stat.semester=:semester", semester)
    query.where("stat.teacher=:me", me)
    query.where("stat.publishStatus>=1")
    query.orderBy("stat.crn")
    val stats = entityDao.search(query)
    put("stats", stats)

    val fbQuery = OqlBuilder.from[Array[Any]](classOf[FinalComment].getName, "fb")
    fbQuery.where("fb.semester=:semester", semester)
    fbQuery.where("fb.teacher=:teacher", me)
    fbQuery.select("fb.crn,count(*)")
    fbQuery.groupBy("fb.crn")
    val feedbackCounts = entityDao.search(fbQuery)
    put("feedbackCounts", feedbackCounts.map(x => x(0) -> x(1)).toMap)
    forward()
  }

  def comments(): View = {
    val me = getTeacher()
    val courseEvalStatId = longId("stat")
    val stat = entityDao.get(classOf[CourseEvalStat], courseEvalStatId)

    val query = OqlBuilder.from(classOf[FinalComment], "fb")
    query.where("fb.semester=:semester", stat.semester)
    query.where("fb.teacher=:teacher", me)
    stat.crn foreach{ crn=>
      query.where("fb.crn=:crn", crn)
    }
    val comments = entityDao.search(query)
    put("courseEvalStat", stat)
    put("comments", comments)
    forward()
  }

  @mapping("info/{id}")
  def info(@param("id") id: Long): View = {
    val stat = entityDao.get(classOf[CourseEvalStat], id)
    val me = getTeacher()
    if (stat.teacher != me || stat.publishStatus < 1) {
      Status.NotFound
    } else {
      val query = OqlBuilder.from(classOf[CategoryEvalStat], "stat")
      query.where("stat.category=:category", stat.category)
      query.where("stat.semester=:semester", stat.semester)
      val categoryStats = entityDao.search(query)
      put("categoryStat", categoryStats.head)

      val query2 = OqlBuilder.from(classOf[DepartEvalStat], "stat")
      query2.where("stat.department=:department", stat.teachDepart)
      query2.where("stat.semester=:semester", stat.semester)
      val departStats = entityDao.search(query2)
      put("departmentStat", departStats.head)

      put("stat", stat)
      forward()
    }
  }

  private def getTeacher(): Teacher = {
    val query = OqlBuilder.from(classOf[Teacher], "t")
    query.where("t.user.code=:code", Securities.user)
    entityDao.search(query).head
  }
}
