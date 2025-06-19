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
import org.beangle.webmvc.annotation.mapping
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Department, Semester}
import org.openurp.code.std.model.StdType
import org.openurp.edu.clazz.model.Clazz
import org.openurp.qos.evaluation.clazz.model.{CourseEvalStat, EvaluateResult, QuestionResult, QuestionnaireClazz}
import org.openurp.qos.evaluation.base.model.{AssessGrade, Indicator, Option}

class QuestionnaireStatTeacherAction extends RestfulAction[CourseEvalStat] {

  override def index(): View = {
    put("stdTypeList", entityDao.getAll(classOf[StdType]))
    put("departmentList", entityDao.search(OqlBuilder.from(classOf[Department], "de").where("de.teaching=:tea", true)))
    val teacher = entityDao.get(classOf[Teacher], 8589L)
    put("teacher", teacher)
    forward()
  }

  override def search(): View = {
    val entityQuery = OqlBuilder.from(classOf[CourseEvalStat], "questionnaireStat")
    populateConditions(entityQuery)
    val teacher = entityDao.get(classOf[Teacher], 8589L)
    entityQuery.join("questionnaireStat.clazz.teachers", "teacher")
    entityQuery.where("teacher.id=:teacherId", teacher.id)
    entityQuery.limit(getPageLimit)
    val orderBy = get("orderBy")
    if (orderBy != null && !"".equals(orderBy)) {
      entityQuery.orderBy("questionnaireStat.clazz.semester.schoolYear desc")
    }
    val questionnaireStatTeachers = entityDao.search(entityQuery)
    put("questionnaireStatTeachers", questionnaireStatTeachers)
    val indicatorList = entityDao.search(OqlBuilder.from(classOf[Indicator], "qt"))
    put("indicatorList", indicatorList)
    put("criteria", entityDao.search(OqlBuilder.from(classOf[AssessGrade], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L)))
    forward()
  }

  /**
   * 教师个人查询自己被评教的详细情况
   */
  def evaluatePersonInfo(): View = {
    val stat = entityDao.get(classOf[CourseEvalStat], getLong("teacherStatId").get)
    put("questionnaireStat", stat)
    val q = OqlBuilder.from(classOf[Clazz], "c")
    q.where("c.project=:project and c.semester=:semester and c.crn=:crn", stat.project, stat.semester, stat.crn)
    val clazz = entityDao.search(q).head
    val query = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName, "result")
    query.where("result.teacher=:teaId", stat.teacher)
    query.where("result.clazz=:less", clazz)

    query.select("case when result.statType =1 then count(result.id) end,count(result.id)")
    query.groupBy("result.statType")
    entityDao.search(query) foreach { a =>
      put("number1", a(0))
      put("number2", a(1))
    }
    val list = Collections.newBuffer[Option]
    val questions = stat.questions
    questions foreach { question =>

      val options = question.optionGroup.options
      options foreach { option =>

        var tt = 0
        list foreach { oldOption =>

          if (oldOption.id.equals(option.id)) {
            tt += 1
          }

        }
        if (tt == 0) {
          list += option
        }

      }

    }
    put("options", list)
    val querys = OqlBuilder.from[Integer](classOf[QuestionnaireClazz].getName, "questionnaireL")
    querys.join("questionnaireL.clazz.teachers", "teacher")
    querys.where("teacher=:teach", stat.teacher)
    querys.where("questionnaireL.project=:p[roject and questionnaireL.semester=:semester and questionnaireL.crn=:crn",
      stat.project, stat.semester, stat.crn)
    querys.join("questionnaireL.clazz.teachclass.courseTakers", "courseTaker")
    querys.select("count(courseTaker.id)")
    put("numbers", entityDao.search(querys)(0))
    val que = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    que.where("questionR.result.teacher=:teaId", stat.teacher)
    que.where("questionnaireL.project=:p[roject and questionnaireL.semester=:semester and questionnaireL.crn=:crn",
      stat.project, stat.semester, stat.crn)
    que.select("questionR.question.id,questionR.option.id,count(*)")
    que.groupBy("questionR.question.id,questionR.option.id")
    put("questionRs", entityDao.search(que))
    val quer = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    quer.where("questionR.result.teacher=:teaId", stat.teacher)
    quer.where("questionR.project=:p[roject and questionR.semester=:semester and questionR.crn=:crn",
      stat.project, stat.semester, stat.crn)
    quer.select("questionR.question.id,questionR.question.contents,sum(questionR.score)/count(questionR.id)")
    quer.groupBy("questionR.question.id,questionR.question.contents")
    put("questionResults", entityDao.search(quer))
    forward()
  }

  /**
   * 教师个人查询自己被评教的详细情况
   */
  @mapping(value = "{id}")
  override def info(id: String): View = {
    val stat = entityDao.get(classOf[CourseEvalStat], id.toLong)
    val teaId = stat.teacher.id
    val semesterId = stat.semester.id
    val cq = OqlBuilder.from(classOf[Clazz], "c")
    cq.where("cq.project=:project and cq.semester=:semester and cq.crn=:crn", stat.project, stat.semester, stat.crn)
    val clazz = entityDao.search(cq).head
    /** 本学期是否评教 */
    val builder = OqlBuilder.from(classOf[EvaluateResult], "evaluateResult")
    builder.where("evaluateResult.clazz.semester.id=" + semesterId)
    builder.select("distinct evaluateResult.questionnaire.id")
    val list = entityDao.search(builder)
    if (list.size == 1) {
    } else {
      redirect("search", "未找到评教记录!")
    }
    var semest: Semester = null
    if (semesterId != 0) {
      semest = entityDao.get(classOf[Semester], semesterId)
    }
    put("semester", semest)
    var teacher: Teacher = null
    if (teaId != 0) {
      teacher = entityDao.get(classOf[Teacher], teaId)
    }
    put("teacher", teacher)
    put("clazz", clazz)
    /** 院系平均分 */
    val querdep = OqlBuilder.from[Float](classOf[EvaluateResult].getName + " evaluateResult," +
      classOf[QuestionResult].getName + " questionResult")
    querdep.select("sum(questionResult.score)/count(distinct evaluateResult.id)")
    querdep.where("evaluateResult.id=questionResult.result.id")
    querdep.where("evaluateResult.clazz.semester.id=" + semesterId)
    if (clazz != null) {
      querdep.where("evaluateResult.clazz.teachDepart.id=:depId", clazz.teachDepart.id)
      // querdep.where("evaluateResult.teacher.teacher.department.id=:depId",teacher.getDepartment().getId())
    }
    put("depScores", entityDao.search(querdep)(0).toString().toFloat)
    /** 全校平均分 */
    val que = OqlBuilder.from[Float](classOf[EvaluateResult].getName + " evaluateResult," +
      classOf[QuestionResult].getName + " questionResult")
    que.select("sum(questionResult.score)/count(distinct evaluateResult.id)")
    que.where("evaluateResult.id=questionResult.result.id")
    que.where("evaluateResult.clazz.semester.id=" + semesterId)
    put("evaResults", entityDao.search(que)(0).toString().toFloat)

    /** 课程全校评教排名 */
    val querSch = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName + " evaluateResult," +
      classOf[QuestionResult].getName + " questionResult")
    querSch.select("evaluateResult.clazz.id,sum(questionResult.score)/count(distinct evaluateResult.id)")
    querSch.where("evaluateResult.id=questionResult.result.id")
    querSch.where("evaluateResult.clazz.semester.id=" + semesterId)
    querSch.groupBy("evaluateResult.clazz.id")
    querSch.orderBy("sum(questionResult.score)/count(distinct evaluateResult.id) desc")
    val schList = entityDao.search(querSch)
    var schNums = 0
    schList foreach { ob =>
      if (ob(0).toString().equals(clazz.id.toString())) {
        schNums += 1
      }

    }
    put("schNum", schNums)
    put("schNums", schList.size)
    /** 课程院系评教排名 */
    val querydep = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult")
    querydep.select("evaluateResult.clazz.id,sum(questionResult.score)/count(distinct evaluateResult.id)")
    querydep.where("evaluateResult.id=questionResult.result.id")
    querydep.where("evaluateResult.clazz.semester.id=" + semesterId)
    if (clazz != null) {
      querdep.where("evaluateResult.clazz.teachDepart.id=:depId", clazz.teachDepart.id)
      // querdep.where("evaluateResult.teacher.teacher.department.id=:depId",teacher.getDepartment().getId())
    }
    querydep.groupBy("evaluateResult.clazz.id")
    querydep.orderBy("sum(questionResult.score)/count(distinct evaluateResult.id) desc")
    val depList = entityDao.search(querydep)
    var depNums = 0
    depList foreach { ob =>

      if (ob(0).toString().equals(clazz.id.toString())) {
        depNums += 1
      }

    }
    put("depNum", depNums)
    put("depNums", depList.size)
    /** 教师评教总分 */
    val quer = OqlBuilder.from[Float](classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult")
    quer.select("sum(questionResult.score)/count(distinct evaluateResult.id)")
    quer.where("evaluateResult.id=questionResult.result.id")
    quer.where("evaluateResult.clazz.semester.id=" + semesterId)

    if (teaId != 0L) {
      quer.where("evaluateResult.teacher.id=:teaId", teaId)
    }
    quer.where("evaluateResult.clazz=:clazz", clazz)
    put("teaScore", entityDao.search(quer)(0))

    /** 教师各项得分 */
    val quer1 = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult")
    quer1.select("questionResult.question.contents,questionResult.question.id,sum(questionResult.score)/count(evaluateResult.id)")
    quer1.where("evaluateResult.id=questionResult.result.id")
    quer1.where("evaluateResult.clazz.semester.id=" + semesterId)
    if (teaId != 0L) {
      quer1.where("evaluateResult.teacher.id=:teaId", teaId)
    }
    quer1.where("evaluateResult.clazz=:clazz", clazz)
    quer1.groupBy("questionResult.question.id,questionResult.question.contents")
    // quer1.orderBy("questionResult.question.priority desc")
    put("questionRList", entityDao.search(quer1))
    /** 院系各项得分 */
    val depQuery = OqlBuilder.from(classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult")
    depQuery.select("questionResult.question.id,sum(questionResult.score)/count(evaluateResult.id)")
    depQuery.where("evaluateResult.id=questionResult.result.id")
    depQuery.where("evaluateResult.clazz.semester.id=" + semesterId)
    if (clazz != null) {
      depQuery.where("evaluateResult.clazz.teachDepart.id=:depsId", clazz.teachDepart.id)
    }
    depQuery.groupBy("questionResult.question.id")
    depQuery.orderBy("questionResult.question.id")
    put("depQRList", entityDao.search(depQuery))
    /** 全校各项得分 */
    val schQuery = OqlBuilder.from(classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult")
    schQuery.select("questionResult.question.id,sum(questionResult.score)/count(evaluateResult.id)")
    schQuery.where("evaluateResult.id=questionResult.result.id")
    schQuery.where("evaluateResult.clazz.semester.id=" + semesterId)
    schQuery.groupBy("questionResult.question.id")
    schQuery.orderBy("questionResult.question.id")
    put("schQRList", entityDao.search(schQuery))
    return forward()
  }

}
