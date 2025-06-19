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
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.dao.OqlBuilder
import org.beangle.doc.transfer.Format.Xlsx
import org.beangle.doc.transfer.exporter.{ExcelTemplateExporter, ExportContext}
import org.beangle.webmvc.context.ActionContext
import org.beangle.webmvc.view.{Status, View}
import org.beangle.webmvc.support.action.ExportSupport
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Project, Semester}
import org.openurp.qos.evaluation.app.course.service.Ranker
import org.openurp.qos.evaluation.clazz.model.{FinalTeacherScore, QuestionResult}
import org.openurp.qos.evaluation.department.model.{DepartEvaluate, SupervisiorEvaluate}

import java.time.LocalDate

class FinalTeacherScoreAction extends ProjectRestfulAction[FinalTeacherScore], ExportSupport[FinalTeacherScore] {

  override def index(): View = {
    given project: Project = getProject

    put("departments", project.departments)
    put("currentSemester", getSemester)
    forward()
  }

  override def search(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val finalScores = OqlBuilder.from(classOf[FinalTeacherScore], "finalTeacherScore")
    populateConditions(finalScores)
    finalScores.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    finalScores.where("finalTeacherScore.semester=:semester", semester)
    put("finalTeacherScores", entityDao.search(finalScores))
    put("semesterId", semesterId)
    forward()
  }

  /**
   * 导出
   */
  override def exportData(): View = {
    val semesterId = getInt("semester.id").get
    val finalScores = OqlBuilder.from(classOf[FinalTeacherScore], "finalTeacherScore")
    populateConditions(finalScores)
    finalScores.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    finalScores.where("finalTeacherScore.semester.id=:semesterId", semesterId)
    val list = scala.jdk.javaapi.CollectionConverters.asJava(entityDao.search(finalScores))
    //查出信息并放到map中
    val context = new ExportContext(Xlsx)
    context.put("list", list)
    //获得模板路径
    val path = ClassLoaders.getResource("template/finalTeacherScore.xls").get
    context.template = path
    //准备输出流
    val response = ActionContext.current.response
    response.setContentType("application/x-excel")
    response.setHeader("Content-Disposition", "attachmentfilename=finalTeacherScore.xls")
    val os = response.getOutputStream()
    //将beans通过模板输入流写到workbook中
    new ExcelTemplateExporter().exportData(os, context)
    Status.Ok
  }

  def rankStat(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)
    //    排名
    val rankQuery = OqlBuilder.from(classOf[FinalTeacherScore], "finalTeacherScore")
    rankQuery.where("finalTeacherScore.semester.id=:semesterId", semesterId)
    val evals = entityDao.search(rankQuery)
    Ranker.rOver(evals) { (x, r) =>
      x.schoolRank = r
    }
    val departEvalMaps = evals.groupBy(x => x.teacher.department)
    departEvalMaps.values foreach { departEvals =>
      Ranker.rOver(departEvals) { (x, r) =>
        x.departRank = r
      }
    }
    entityDao.saveOrUpdate(evals)
    redirect("index", "info.action.success")
  }

  /**
   * 跳转(统计首页面)
   */
  def statHome(): View = {
    given project: Project = getProject

    put("stdTypeList", project.stdTypes)
    put("departmentList", project.departments)

    put("educations", project.levels)
    put("departments", project.departments)

    put("currentSemester", getSemester)
    forward()
  }

  def stat(): View = {

    val semesterId = getInt("semester.id").get
    val semester = entityDao.get(classOf[Semester], semesterId)

    // 删除历史统计数据
    remove(semesterId)
    //    怎么保证TeacherEvalStat/DepartEvaluate/SupervisiorEvaluate中每个教师只有一个成绩
    //    一个老师可能有两个不同的问卷？

    val quer = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName + " questionR,"
      + classOf[DepartEvaluate].getName + " departEvaluate,"
      + classOf[SupervisiorEvaluate].getName + " supervisiorEvaluate")

    quer.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    quer.where("questionR.result.statType is 1")
    quer.select("questionR.result.teacher.id,"
      //        + "sum(questionR.score),case when questionR.result.statType =1 then count(distinct questionR.result.id) end,"
      //        + "count(distinct questionR.result.id),case when questionR.result.statType =1 then sum(questionR.score) end,"
      + "supervisiorEvaluate.totalScore,departEvaluate.totalScore,"
      + "sum(questionR.score)/count(distinct questionR.result.id)")
    quer.where("questionR.result.clazz.semester.id=departEvaluate.semester.id")
    quer.where("questionR.result.teacher.id=departEvaluate.teacher.id")
    quer.where("questionR.result.clazz.semester.id=supervisiorEvaluate.semester.id")
    quer.where("questionR.result.teacher.id=supervisiorEvaluate.teacher.id")
    quer.where("questionR.result.clazz.semester.id =:semesterId", semesterId)
    quer.groupBy("questionR.result.teacher.id,supervisiorEvaluate.totalScore,departEvaluate.totalScore")
    //    val wjStat = entityDao.search(quer)

    //    val que = OqlBuilder.from[Array[Any]](classOf[TeacherEvalStat].getName + " teacherEvalStat,"
    //        + classOf[DepartEvaluate].getName + " departEvaluate,"
    //        + classOf[SupervisiorEvaluate].getName + " supervisiorEvaluate")
    //    que.select("teacherEvalStat.teacher.id,teacherEvalStat.score,supervisiorEvaluate.totalScore,departEvaluate.totalScore")
    //    que.where("teacherEvalStat.semester.id=departEvaluate.semester.id")
    //    que.where("teacherEvalStat.teacher.id=departEvaluate.teacher.id")
    //    que.where("teacherEvalStat.semester.id=supervisiorEvaluate.semester.id")
    //    que.where("teacherEvalStat.teacher.id=supervisiorEvaluate.teacher.id")
    //    que.where("teacherEvalStat.semester.id =:semesterId", semesterId)

    //    val finalScoreMap = new collection.mutable.HashMap[Long,Buffer[Tuple4[Number,Number,Number,Number]]]
    //    entityDao.search(que) foreach { a =>
    //      val buffer = finalScoreMap.getOrElseUpdate(a(0).asInstanceOf[Long],new ListBuffer[Tuple4[Number,Number,Number,Number]])
    //      buffer += Tuple4(a(1).asInstanceOf[Number],a(2).asInstanceOf[Number],a(3).asInstanceOf[Number],(a(1).toString().toFloat)*0.5+(a(2).toString().toFloat)*0.3+(a(3).toString().toFloat)*0.2)
    //    }
    val finalScores = entityDao.search(quer)
    finalScores foreach { ob =>
      val questionS = new FinalTeacherScore
      questionS.teacher = new Teacher()
      questionS.teacher.id = ob(0).asInstanceOf[Long]
      questionS.semester = semester
      questionS.stdScore = ob(3).toString().toFloat * 10
      questionS.supviScore = ob(1).toString().toFloat
      questionS.departScore = ob(2).toString().toFloat
      questionS.score = (ob(3).toString().toFloat * 10 * 0.5 + ob(1).toString().toFloat * 0.3 + ob(2).toString().toFloat * 0.2).toFloat
      entityDao.saveOrUpdate(questionS)
    }
    redirect("index", "info.action.success")
  }

  /**
   * 清除统计数据
   */
  def remove(semesterId: Int): Unit = {
    val query = OqlBuilder.from(classOf[FinalTeacherScore], "finalScore")
    query.where("finalScore.semester.id=:semesterId", semesterId)
    entityDao.remove(entityDao.search(query))
  }
}
