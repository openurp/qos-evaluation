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
import org.beangle.security.Securities
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Project, Semester}
import org.openurp.edu.clazz.model.{Clazz, CourseTaker}
import org.openurp.qos.evaluation.app.course.model.EvaluateSearchDepartment
import org.openurp.qos.evaluation.clazz.model.EvaluateResult
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction

import java.text.DecimalFormat
import java.time.LocalDate

class EvaluateStatusTeacherAction extends ProjectRestfulAction[EvaluateResult] {

  override def index(): View = {
    given project: Project = getProject

    put("currentSemester", this.getSemester)
    forward()
  }

  /**
   * 教师评教回收情况
   * //
   */
  override def search(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val teacherQuery = OqlBuilder.from(classOf[Teacher], "t")
    teacherQuery.where("t.project=:project and t.staff.code=:code", getProject, Securities.user)
    val teacher = entityDao.search(teacherQuery).headOption.orNull
    if (teacher == null) {
      forward("error.teacher.teaNo.needed")
    }
    // 得到院系下的所有级教学任务
    val clazzQuery = OqlBuilder.from(classOf[Clazz], "clazz")
    clazzQuery.where("clazz.semester.id=:semesterId", semesterId)
    if (teacher != null) {
      clazzQuery.join("clazz.teachers", "teacher")
      clazzQuery.where("teacher =:teacher", teacher)
    }
    val clazzList = entityDao.search(clazzQuery)
    val evaluateSearchDepartmentList = Collections.newBuffer[EvaluateSearchDepartment]
    clazzList foreach { clazz =>

      var countAll: Long = 0L
      var haveFinish: Long = 0L
      val query = OqlBuilder.from[Long](classOf[CourseTaker].getName, "courseTake")
      query.select("select count(*)")
      query.where("courseTake.clazz =:clazz", clazz)
      //      query.where("courseTake.std.state.department.id=:depIds", departmentId)
      //      query.where("exists( from "+classOf[QuestionnaireClazz].getName +" questionnaireClazz where questionnaireClazz.clazz=courseTake.clazz)")
      //      query.where("courseTake.std.state.adminclass =:adminClass)", adminClass)
      //      query.orderBy(Order.parse(get("orderBy")))
      val list = entityDao.search(query)
      // 得到指定学期，院系的学生评教人次总数
      countAll = list(0)

      val query1 = OqlBuilder.from[Long](classOf[EvaluateResult].getName, "rs")
      query1.select("select count(*)")
      query1.where("rs.clazz =:clazz", clazz)
      //      query1.where("rs.student.state.department in(:departments)", department)
      //      query1.where("rs.student.state.adminclass =:adminClass)", adminClass)
      //      query1.orderBy(Order.parse(get("orderBy")))
      val list1 = entityDao.search(query1)
      // 得到指定学期，已经评教的学生人次数
      haveFinish = list1(0)
      var finishRate = ""
      if (countAll != 0) {
        val df = new DecimalFormat("0.0")
        finishRate = df.format((haveFinish * 100 / countAll).toFloat) + "%"
      }
      val esd = new EvaluateSearchDepartment()
      esd.semester = semester
      esd.clazz = clazz
      esd.countAll = countAll
      esd.haveFinish = haveFinish
      esd.finishRate = finishRate
      evaluateSearchDepartmentList += esd

    }
    // Collections.sort(evaluateSearchDepartmentList, new PropertyComparator("adminClass.code"))
    evaluateSearchDepartmentList.sortWith((x, y) => x.clazz.course.code < y.clazz.course.code)
    put("evaluateSearchDepartmentList", evaluateSearchDepartmentList)
    put("semester", semester)
    forward()
  }

  //  /**
  //   * 班级学生评教回收情况查询
  //   */
  //  def  adminClassSearch():String= {
  //    val semesterId = getInt("semester.id").get
  //    val date = new java.util.Date()
  //    var semester:Semester = null
  //    if (semesterId != 0) {
  //      semester =  entityDao.get(classOf[Semester], semesterId)
  //    }
  //    // 根据班级代码得到班级
  //    val adminClassId = getLong("adminclass.id").get
  //    var adminClass:Adminclass = null
  //    if (adminClassId != 0L) {
  //      adminClass =  entityDao.get(classOf[Adminclass], adminClassId)
  //    }
  //    // 得到班级所有的学生
  //    val  studentQuery = OqlBuilder.from(classOf[Student], "student")
  //     studentQuery.where("student.state.adminclass=:adminClass", adminClass)
  ////    studentQuery.where("student.enrollOn<=:enON", date)
  ////    studentQuery.where("student.graduateOn>=:geON", date)
  ////    studentQuery.where("student.project.id =:project", 1)
  //    // studentQuery.where("student.inSchool is true")
  //    val studentList = entityDao.search(studentQuery)
  //
  //    val evaluateSearchAdminClassList = Collections.newBuffer[EvaluateSearchAdminclass]
  //    if (studentList.size > 0) {
  //      // 得到指定学期，院系的学生评教人次总数
  //      val query = OqlBuilder.from[Array[Any]](classOf[CourseTaker].getName, "courseTake")
  //      query.select("courseTake.std.id,count(*)")
  //      query.where("courseTake.clazz.semester.id=:semesterId", semesterId)
  //      if (studentList.size > 0) {
  //        query.where("courseTake.std in (:students)", studentList)
  //      }
  ////      query.where("exists ( from "+classOf[QuestionnaireClazz].getName +" questionn where questionn.clazz = courseTake.clazz)")
  //      query.groupBy("courseTake.std.id")
  //      val list = entityDao.search(query)
  //      // 得到指定学期，已经评教的学生人次数
  //      val query1 = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName, "rs")
  //      query1.select("rs.student.id,count(distinct rs.clazz.id)")
  //      query1.where("rs.clazz.semester.id =:semesterId", semesterId)
  //      if (studentList.size > 0) {
  //        query1.where("rs.student in (:students)", studentList)
  //      }
  //      query1.groupBy("rs.student.id")
  //      val list1 = entityDao.search(query1)
  //      studentList foreach {student =>
  //        // 得到指定学期，院系的学生评教人次总数
  //        var countAll = 0
  //        var haveFinish = 0
  //        var finishRate = 0f
  //        list foreach { ob =>
  //          if (ob(0).equals(student.id)) {
  //            countAll = ob(1).toString().toInt
  //          }
  //        }
  //
  //        // 得到指定学期，已经评教的学生人次数
  //
  //        list1 foreach { ob2 =>
  //          if (ob2(0).equals(student.id)) {
  //            haveFinish = ob2(1).toString().toInt
  //          }
  //        }
  //
  //        if (countAll != 0) {
  //          finishRate = (haveFinish.toFloat / countAll.toFloat) * 100f
  //        }
  //        val esc = new EvaluateSearchAdminclass()
  //        esc.semester=semester
  //        esc.student=student
  //        esc.countAll=countAll
  //        esc.haveFinish=haveFinish
  //        esc.finishRate=finishRate
  //        evaluateSearchAdminClassList += esc
  //      }
  ////      Collections.sort(evaluateSearchAdminClassList, new PropertyComparator("finishRate"))
  //      evaluateSearchAdminClassList.sortWith((x,y)=>x.finishRate<y.finishRate)
  //    }
  //    put("semester", semester)
  //    put("evaluateSearchAdminClassList", evaluateSearchAdminClassList)
  //    return forward()
  //  }

  //  /**
  //   * 学生评教详情
  //   */
  //  def  info():String= {
  //    val semesterId = getInt("semester.id").get
  //    val stuId = getLong("stuIds").get
  //    val semester =entityDao.get(classOf[Semester], semesterId)
  //    // 得到指定学期，院系的学生评教人次总数
  //    val query = OqlBuilder.from(classOf[CourseTaker], "courseTake")
  //    query.where("courseTake.clazz.semester =:semester", semester)
  //    query.where("courseTake.std.id =:stdId", stuId)
  ////    query.where("exists ( from "+classOf[QuestionnaireClazz].getName +" questionn where questionn.clazz = courseTake.clazz)")
  //    val list = entityDao.search(query)
  //    // 得到指定学期，已经评教的学生人次数
  //    val query1 = OqlBuilder.from(classOf[EvaluateResult], "rs")
  //    query1.where("rs.clazz.semester =:semester", semester)
  //    query1.where("rs.student.id =:stuId", stuId)
  //    val list1 = entityDao.search(query1)
  //    put("questionnaireTaskList", list)
  //    put("evaluateResultList", list1)
  //    return forward()
  //  }

}
