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

import org.beangle.commons.collection.Order
import org.beangle.commons.lang.Numbers
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.annotation.param
import org.beangle.webmvc.view.View
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Project, Semester}
import org.openurp.edu.exam.model.ExamTaker
import org.openurp.qos.evaluation.clazz.model.{EvaluateResult, QuestionResult}
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction

import java.time.LocalDate

class EvaluateResultAction extends ProjectRestfulAction[EvaluateResult] {

  override def search(): View = {
    // 页面条件
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val statType = getInt("statType").getOrElse(null)
    val evaluateResult = OqlBuilder.from(classOf[EvaluateResult], "evaluateResult")
    populateConditions(evaluateResult)
    evaluateResult.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    evaluateResult.where("evaluateResult.clazz.semester=:semester", semester)
    if (statType != null) {
      evaluateResult.where("evaluateResult.statType=:statType", statType)
    }
    put("evaluateResults", entityDao.search(evaluateResult))
    forward()
  }

  /**
   * 修改(评教结果,是否有效)
   *
   * @return
   */
  def updateState(): View = {
    val ids = getLongIds("evaluateResult")
    var state = getInt("isEvaluate").get
    val results = entityDao.find(classOf[EvaluateResult], ids)
    results foreach { result =>
      result.statType = state
    }
    try {
      entityDao.saveOrUpdate(results)
      return redirect("search", "info.action.success")
    } catch {
      case e: Exception =>
        return redirect("search", "info.save.failure")
    }
  }

  /**
   * 更改教师
   */
  def updateTeacher(): View = {
    put("evaluateR", entityDao.get(classOf[EvaluateResult], getLong("evaluateResult").get))
    // put("departments",entityDao.get(Department.class, "",))
    forward()
  }

  def saveEvaluateTea(): View = {
    try {
      val evaluateR = entityDao.get(classOf[EvaluateResult], getLong("evaluateResult").get)
      evaluateR.teacher = entityDao.get(classOf[Teacher], getLong("teacher.id").get)
      entityDao.saveOrUpdate(evaluateR)
    } catch {
      case e: Exception =>
        // TODO: handle exception
        return redirect("search", "info.save.failure")
    }
    return redirect("search", "info.action.success")
  }

  /**
   * 查看(详细信息)
   */
  override def info(@param("id") id: String): View = {
    if (null == id) {
      logger.warn("cannot get paremeter {}Id or {}.id")
    }
    val resultId = Numbers.toLong(id)
    val result = entityDao.get(classOf[EvaluateResult], Numbers.toLong(id))
    val query = OqlBuilder.from(classOf[QuestionResult], "questionResult")
    query.where("questionResult.result =:result", result)
    val questionResults = entityDao.search(query)
    questionResults.sortWith((x, y) => x.question.priority < y.question.priority)
    put("questionResults", questionResults)
    //    put("remark", result.remark)
    forward()
  }

  /**
   * 将没有考试资格的学生评教问卷改为无效
   *
   * @return
   */
  def changeToInvalid(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val query = OqlBuilder.from(classOf[EvaluateResult], "evaluateResult")
    query.where("evaluateResult.clazz.semester.id = :semesterId", semesterId)
    val results = entityDao.search(query)
    try {
      results foreach { result =>
        //      for (EvaluateResult result : results) {
        //        // TODO kang 怎么确定学生有没有一门课的考试资格
        val builder = OqlBuilder.from(classOf[ExamTaker], "examTaker")
        builder.where("examTaker.clazz=:clazz", result.clazz)
        builder.where("examTaker.std=:std", result.student)
        val takes = entityDao.search(builder)
        if (takes.size > 0) {
          if ("违纪".equals(takes.head.examStatus.name)) {
            //FIXME
            //result.statType=false
            result.statType = 0
          }
        }
      }
      saveOrUpdate(results)
    } catch {
      case e: Exception =>
        return redirect("search", "info.action.failure")
    }
    return redirect("search", "info.action.success")
  }

  override protected def indexSetting(): Unit = {
    given project: Project = getProject

    put("project", project)
    put("currentSemester", getSemester)
  }
}
