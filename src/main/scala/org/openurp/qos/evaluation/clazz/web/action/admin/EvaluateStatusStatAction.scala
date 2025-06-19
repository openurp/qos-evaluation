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
import org.beangle.commons.lang.Strings
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.view.View
import org.openurp.base.model.{Department, Project, Semester}
import org.openurp.edu.clazz.model.{Clazz, CourseTaker}
import org.openurp.qos.evaluation.app.course.model.{EvaluateSearchDepartment, EvaluateSearchManager}
import org.openurp.qos.evaluation.clazz.model.EvaluateResult
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction

import java.text.DecimalFormat
import java.time.LocalDate

class EvaluateStatusStatAction extends ProjectRestfulAction[EvaluateResult] {

  override def index(): View = {
    given project: Project = getProject

    put("currentSemester",getSemester)
    put("departments", findInSchool(classOf[Department]))
    forward()
  }

  override def search(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val departmentId = getInt("department.id").getOrElse(0)
    val clazzNo = get("clazz.crn").getOrElse("")
    val courseCode = get("course.code").getOrElse("")
    val courseName = get("course.name").getOrElse("")
    val teacherName = get("teacher.name").getOrElse("")
    if (departmentId == 0 && clazzNo == "" && courseCode == "" && courseName == "" && teacherName == "") {
      searchSchool(semesterId)
    } else {
      searchDep(semesterId)
      return forward("searchDep")
    }
    forward()
  }
  //按照开课院系、教学任务查看回收率
  def searchDep(semesterId: Int) = {
    val semester = entityDao.get(classOf[Semester], semesterId)
    val departmentId = getInt("department.id").getOrElse(0)
    //      entityDao.get(classOf[Department], departmentId)
    val clazzNo = get("clazz.crn").get
    val courseCode = get("course.code").get
    val courseName = get("course.name").get
    val teacherName = get("teacher.name").get
    // 得到院系下的所有教学任务
    val clazzQuery = OqlBuilder.from(classOf[Clazz], "clazz")
    clazzQuery.where("clazz.semester =:semester", semester)
    if (departmentId != 0) {
      clazzQuery.where("clazz.teachDepart.id=:depIds", departmentId)
    }
    if (Strings.isNotBlank(clazzNo)) {
      clazzQuery.where("clazz.crn =:clazzNo", clazzNo)
    }
    if (Strings.isNotBlank(courseCode)) {
      clazzQuery.where("clazz.course.code =:courseCode", courseCode)
    }
    if (Strings.isNotBlank(courseName)) {
      clazzQuery.where("clazz.course.name like :courseName", "%" + courseName + "%")
    }
    if (Strings.isNotBlank(teacherName)) {
      clazzQuery.join("clazz.teachers", "teacher")
      clazzQuery.where("teacher.name like :teacherName", "%" + teacherName + "%")
    }
    val clazzList = entityDao.search(clazzQuery)
    val evaluateSearchDepartmentList = Collections.newBuffer[EvaluateSearchDepartment]
    clazzList foreach { clazz =>

      var countAll: Long = 0L
      var haveFinish: Long = 0L
      var stdFinish: Long = 0L
      val query = OqlBuilder.from[Long](classOf[CourseTaker].getName, "courseTake")
      query.select("count(*)")
      query.where("courseTake.clazz =:clazz", clazz)
      //      query.where("courseTake.clazz.teachDepart.id=:depIds", departmentId)
      //      query.where("exists( from "+classOf[QuestionnaireClazz].getName +" questionnaireClazz where questionnaireClazz.clazz=courseTake.clazz)")
      //      query.orderBy(Order.parse(get("orderBy")))
      val list = entityDao.search(query)
      // 得到指定学期，院系的学生评教人次总数
      countAll = list(0)

      val query1 = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName, "rs")
      query1.select("count(*),count(distinct student_id)")
      query1.where("rs.clazz =:clazz", clazz)
      //      query1.where("rs.student.state.department in(:departments)", department)
      //      query1.where("rs.student.state.adminclass =:adminClass)", adminClass)
      //      query1.orderBy(Order.parse(get("orderBy")))
      entityDao.search(query1) foreach { list1 =>
        // 得到指定学期，已经评教的学生人次数
        haveFinish = list1(0).asInstanceOf[Long]
        stdFinish = list1(1).asInstanceOf[Long]
      }
      var finishRate = ""
      if (countAll != 0) {
        val df = new DecimalFormat("0.0")
        finishRate = df.format((haveFinish * 100 / countAll).toFloat) + "%"
      }
      if (finishRate != "") {
        val esd = new EvaluateSearchDepartment()
        esd.semester = semester
        esd.clazz = clazz
        esd.countAll = countAll
        esd.haveFinish = haveFinish
        esd.stdFinish = stdFinish
        esd.finishRate = finishRate
        evaluateSearchDepartmentList += esd
      }
    }
    //
    // Collections.sort(evaluateSearchDepartmentList, new PropertyComparator("adminClass.code"))
    put("evaluateSearchDepartmentList", evaluateSearchDepartmentList)
  }

  def searchSchool(semesterId: Int) = {
    val evaluateSearchManagerList = Collections.newBuffer[EvaluateSearchManager]
    val semester = entityDao.get(classOf[Semester], semesterId)
    val departQuery = OqlBuilder.from(classOf[Department], "department")
    departQuery.where("department.teaching =:teaching", true)
    // departQuery.where("department.level =:lever", 1)
    // departQuery.where("department.enabled =:enabled", true)
    val departmentList = entityDao.search(departQuery)

    departmentList foreach { department =>
      var countAll: Long = 0L
      var stdAll: Long = 0L
      var haveFinish: Long = 0L
      var stdFinish: Long = 0L
      //总评人次
      val query = OqlBuilder.from[Array[Any]](classOf[CourseTaker].getName, "courseTake")
      query.select("count(*),count(distinct std)")
      query.where("courseTake.clazz.semester =:semester", semester)
      query.where("courseTake.clazz.teachDepart =:manageDepartment", department)
      //      query.where("exists( from "+classOf[QuestionnaireClazz].getName +" questionnaireClazz where questionnaireClazz.clazz=courseTake.clazz)")
      entityDao.search(query) foreach { list =>
        // 得到指定学期，院系的学生评教人次总数
        countAll = list(0).asInstanceOf[Long]
        stdAll = list(1).asInstanceOf[Long]
      }
      val query1 = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName, "rs")
      query1.select("count(*),count(distinct student)")
      query1.where("rs.clazz.semester =:semester", semester)
      query1.where("rs.clazz.teachDepart =:manageDepartment", department)
      entityDao.search(query1) foreach { list1 =>
        // 得到指定学期，已经评教的学生人次数
        haveFinish = list1(0).asInstanceOf[Long]
        stdFinish = list1(1).asInstanceOf[Long]
      }
      var finishRate = ""
      var stdRate = ""
      if (countAll != 0L) {
        val df = new DecimalFormat("0.0")
        finishRate = df.format((haveFinish * 100 / countAll).toFloat) + "%"
        stdRate = df.format((stdFinish * 100 / stdAll).toFloat) + "%"
      }
      if (finishRate != "") {
        val esm = new EvaluateSearchManager()
        esm.semester = semester
        esm.department = department
        esm.countAll = countAll
        esm.stdAll = stdAll
        esm.haveFinish = haveFinish
        esm.stdFinish = stdFinish
        esm.finishRate = finishRate
        esm.stdRate = stdRate
        evaluateSearchManagerList += esm
      }

    }
    put("evaluateSearchManagerList", evaluateSearchManagerList)
  }

}
