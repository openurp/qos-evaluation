/*
 * OpenURP, Agile University Resource Planning Solution.
 *
 * Copyright © 2014, The OpenURP Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openurp.edu.evaluation.clazz.web.action

import java.time.{Instant, LocalDate}

import org.beangle.commons.collection.{Collections, Order}
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.api.annotation.mapping
import org.beangle.webmvc.api.view.View
import org.openurp.base.model.Department
import org.openurp.code.edu.model.EducationLevel
import org.openurp.base.edu.code.model.StdType
import org.openurp.base.edu.model.{Project, Semester, Teacher}
import org.openurp.edu.evaluation.app.course.service.Ranker
import org.openurp.edu.evaluation.clazz.result.model.{EvaluateResult, QuestionResult}
import org.openurp.edu.evaluation.clazz.stat.model._
import org.openurp.edu.evaluation.model.{EvaluationCriteriaItem, Option, Question, QuestionType, Questionnaire}

import scala.collection.mutable.{Buffer, ListBuffer}

class TeacherEvalStatAction extends ProjectRestfulAction[TeacherEvalStat] {

  override def index(): View = {
    put("project",getProject)
    put("departments", findInSchool(classOf[Department]))
    put("questionnaires", entityDao.getAll(classOf[Questionnaire]))
    put("currentSemester", getCurrentSemester)
    forward()
  }

  override def search(): View = {
    val queryBuilder = OqlBuilder.from(classOf[TeacherEvalStat], "teacherEvalStat")
    populateConditions(queryBuilder)
    queryBuilder.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    put("teacherEvalStats", entityDao.search(queryBuilder))
    forward()
  }

  @mapping(method = "delete")
  override def remove(): View = {
    val questionSIds = longIds("teacherEvalStat")
    val query = OqlBuilder.from(classOf[TeacherEvalStat], "questionS")
    query.where("questionS.id in(:ids)", questionSIds)
    entityDao.remove(entityDao.search(query))
    redirect("search", "info.remove.success")
  }

  /**
   * 清除统计数据
   */
  def remove(project: Project, semester: Semester): Unit = {
    val query = OqlBuilder.from(classOf[TeacherEvalStat], "questionS")
    query.where("questionS.semester =:semester", semester)
    query.where("questionS.teacher.project =:project", project)
    entityDao.remove(entityDao.search(query))
  }

  /**
   * 跳转(统计首页面)
   */
  def statHome(): View = {
    put("stdTypeList", entityDao.getAll(classOf[StdType]))
    put("departmentList", entityDao.getAll(classOf[Department]))

    put("educations", entityDao.getAll(classOf[EducationLevel]))
    val teachingDeparts = entityDao.search(OqlBuilder.from(classOf[Department], "depart").where("depart.teaching =:tea", true))
    put("departments", teachingDeparts)

    val builder = OqlBuilder.from(classOf[Semester], "semester")
    builder.where("semester.calendar.school=:school", getProject.school)
    val semesters = entityDao.search(builder)
    put("semesters", semesters)
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    put("currentSemester", entityDao.search(semesterQuery).head)
    forward()
  }

  /**
   * 统计(教师评教结果)
   *
   * @return
   */
  def stat(): View = {
    val semesterId = getInt("semester.id").get
    val semester = entityDao.get(classOf[Semester], semesterId)
    // 删除历史统计数据
    val project = this.getProject
    remove(project, semester)

    // 问卷得分统计
    val quer = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    quer.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    quer.where("questionR.result.statType is 1")
    quer.select("questionR.result.teacher.id,questionR.result.questionnaire.id,"
      + "sum(questionR.score), sum(questionR.score)/count(distinct questionR.result.id)," +
      " count(distinct questionR.result.id)")
    quer.groupBy("questionR.result.teacher.id,questionR.result.questionnaire.id")
    val wjStat = entityDao.search(quer)

    // 问题得分统计
    val que = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    que.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    que.where("questionR.result.statType is 1")
    que.select("questionR.result.teacher.id,questionR.result.questionnaire.id,questionR.question.id,sum(questionR.score),avg(questionR.score),count(questionR.id)")
    que.groupBy("questionR.result.teacher.id,questionR.result.questionnaire.id,questionR.question.id")
    val wtStatMap = new collection.mutable.HashMap[Tuple2[Any, Any], Buffer[Tuple4[Long, Number, Number, Number]]]
    entityDao.search(que) foreach { a =>
      val buffer = wtStatMap.getOrElseUpdate((a(0), a(1)), new ListBuffer[Tuple4[Long, Number, Number, Number]])
      buffer += Tuple4(a(2).asInstanceOf[Long], a(3).asInstanceOf[Number], a(4).asInstanceOf[Number], a(5).asInstanceOf[Number])
    }

    // 问题类别统计
    val tyquery = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    tyquery.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    tyquery.where("questionR.result.statType is 1")
    tyquery.where("questionR.result.teacher is not null")
    tyquery.select("questionR.result.teacher.id,questionR.result.questionnaire.id,questionR.question.questionType.id,sum(questionR.score),sum(questionR.score)/count(distinct questionR.result.id)")
    tyquery.groupBy("questionR.result.teacher.id,questionR.result.questionnaire.id,questionR.question.questionType.id")

    val typeStatMap = new collection.mutable.HashMap[Tuple2[Any, Any], Buffer[Tuple3[Long, Number, Number]]]
    entityDao.search(tyquery) foreach { a =>
      val buffer = typeStatMap.getOrElseUpdate((a(0), a(1)), new ListBuffer[Tuple3[Long, Number, Number]])
      buffer += Tuple3(a(2).asInstanceOf[Long], a(3).asInstanceOf[Number], a(4).asInstanceOf[Number])
    }
    // 选项统计
    val opQuery = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    opQuery.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    opQuery.where("questionR.result.statType is 1")
    opQuery.select("questionR.result.teacher.id,questionR.result.questionnaire.id,questionR.question.id,questionR.option.id,count(questionR.id)")
    opQuery.groupBy("questionR.result.teacher.id,questionR.result.questionnaire.id,questionR.question.id,questionR.option.id")
    val optionStatMap = new collection.mutable.HashMap[Tuple3[Any, Any, Any], Buffer[Tuple2[Long, Number]]]
    entityDao.search(opQuery) foreach { a =>
      val buffer = optionStatMap.getOrElseUpdate((a(0), a(1), a(2)), new ListBuffer[Tuple2[Long, Number]])
      buffer += Tuple2(a(3).asInstanceOf[Long], a(4).asInstanceOf[Number])
    }

    val questionMap = entityDao.getAll(classOf[Question]).map(o => (o.id, o)).toMap
    val questiontyMap = entityDao.getAll(classOf[QuestionType]).map(o => (o.id, o)).toMap
    val optionMap = entityDao.getAll(classOf[Option]).map(o => (o.id, o)).toMap
    //教师问卷得分统计
    wjStat foreach { evaObject =>
      val questionS = new TeacherEvalStat
      questionS.teacher = new Teacher()
      questionS.teacher.id = evaObject(0).asInstanceOf[Long]
      questionS.semester = semester
      questionS.statAt = Instant.now

      questionS.totalScore = evaObject(2).toString().toFloat
      val avgScore = evaObject(3).toString().toFloat
      questionS.avgScore = (Math.round(avgScore * 100) * 1.0 / 100).floatValue
      questionS.tickets = Integer.valueOf(evaObject(4).toString())
      questionS.totalTickets = questionS.tickets
      // 添加问卷
      questionS.questionnaire = new Questionnaire()
      questionS.questionnaire.id = evaObject(1).asInstanceOf[Long]
      // 添加问题得分统计
      val questionDetailStats = Collections.newBuffer[QuestionStat]
      wtStatMap.get((questionS.teacher.id, questionS.questionnaire.id)) foreach { buffer =>
        buffer foreach { wt =>
          val detailStat = new TeacherQuestionStat
          // 添加问题
          detailStat.question = questionMap(wt._1)
          detailStat.totalScore = wt._2.toString().toFloat
          detailStat.avgScore = wt._3.toString().toFloat
          detailStat.evalStat = questionS

          // 添加选项统计
          val optionStates = Collections.newBuffer[OptionStat]
          optionStatMap.get((questionS.teacher.id, questionS.questionnaire.id, detailStat.question.id)) foreach { buffer =>
            buffer foreach { os =>
              val optionstat = new TeacherOptionStat
              optionstat.amount = os._2.intValue()
              optionstat.option = optionMap(os._1)
              optionstat.questionStat = detailStat
              optionStates += optionstat
            }
          }
          detailStat.optionStats = optionStates
          questionDetailStats += detailStat
        }
      }
      questionS.questionStats = questionDetailStats
      //添加问题类别统计
      val questionTypeStats = Collections.newBuffer[QuestionTypeStat]
      typeStatMap.get((questionS.teacher.id, questionS.questionnaire.id)) foreach { buffer =>
        buffer foreach { os =>
          val questionTs = new TeacherQuestionTypeStat
          questionTs.totalScore = os._2.toString().toFloat
          questionTs.avgScore = os._3.toString().toFloat
          questionTs.evalStat = questionS
          questionTs.questionType = questiontyMap(os._1)
          questionTypeStats += questionTs
        }
      }
      questionS.questionTypeStats = questionTypeStats
      entityDao.saveOrUpdate(questionS)
    }
    //    //排名
    //    val rankQuery = OqlBuilder.from(classOf[TeacherEvalStat], "teacherEvalStat")
    //    rankQuery.where("teacherEvalStat.semester.id=:semesterId", semesterId)
    //    val evals = entityDao.search(rankQuery)
    //    Ranker.over(evals) { (x, r) =>
    //      x.rank = r
    //    }
    //    val departEvalMaps = evals.groupBy(x => x.teacher.user.department)
    //    departEvalMaps.values foreach { departEvals =>
    //      Ranker.over(departEvals) { (x, r) =>
    //        x.departRank = r
    //      }
    //    }
    //    entityDao.saveOrUpdate(evals)

    redirect("index", "info.action.success")
  }

  def rankStat(): View = {
    val semesterId = getInt("semester.id").get
    val semester = entityDao.get(classOf[Semester], semesterId)
    //排名
    val rankQuery = OqlBuilder.from(classOf[TeacherEvalStat], "teacherEvalStat")
    rankQuery.where("teacherEvalStat.semester.id=:semesterId", semesterId)
    val evals = entityDao.search(rankQuery)
    Ranker.over(evals) { (x, r) =>
      x.schoolRank = r
    }
    val departEvalMaps = evals.groupBy(x => x.teacher.user.department)
    departEvalMaps.values foreach { departEvals =>
      Ranker.over(departEvals) { (x, r) =>
        x.departRank = r
      }
    }
    entityDao.saveOrUpdate(evals)
    redirect("index", "info.action.success")
  }

  /**
   * 教师历史评教
   */
  def evaluateTeachHistory(): View = {
    val id = getLong("teacherEvalStat.id").get
    val questionnaires = entityDao.get(classOf[TeacherEvalStat], id)
    val query = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaires")
    query.where("questionnaires.teacher.id=:teaIds", questionnaires.teacher.id)
    query.orderBy("questionnaires.semester.beginOn")
    put("teacher", questionnaires.teacher)
    put("teachEvaluates", entityDao.search(query))
    forward()
  }

  /**
   * 院系教师评教统计
   */
  def departmentChoiceConfig(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val lis = entityDao.search(OqlBuilder.from(classOf[EvaluationCriteriaItem], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) { redirect("search", "未找到评价标准！") }
    put("criterias", lis)
    put("departments", entityDao.search(OqlBuilder.from(classOf[Department], "depart").where("depart.teaching =:teaching", true)))
    put("semester", semester)

    val query = OqlBuilder.from(classOf[TeacherEvalStat], "evaluateR")
    query.select("evaluateR.teacher.user.department.id,count( evaluateR.teacher.id)")
    query.where("evaluateR.semester.id =:semesterId ", semesterId)
    query.groupBy("evaluateR.teacher.user.department.id,evaluateR.semester.id")
    put("questionNums", entityDao.search(query))

    val maps = Collections.newMap[String, Seq[TeacherEvalStat]]
    lis foreach { evaluationCriteriaItem =>
      val query = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaireStat")
      query.where("questionnaireStat.semester.id=:semesterId", semesterId)
      query.select("questionnaireStat.teacher.user.department.id,count(questionnaireStat.teacher.id)")
      query.where("questionnaireStat.score>=" + evaluationCriteriaItem.minScore
        + " and questionnaireStat.score<" + evaluationCriteriaItem.maxScore)
      query.groupBy("questionnaireStat.teacher.user.department.id")
      maps.put(evaluationCriteriaItem.id.toString(), entityDao.search(query))
    }
    put("questionDeps", maps)
    forward()
  }

  /**
   * 院系历史评教
   */
  def depHistoryStat(): View = {
    val lis = entityDao.search(OqlBuilder.from(classOf[EvaluationCriteriaItem], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) { redirect("search", "未找到评价标准！") }
    put("criterias", lis)
    val depId = getInt("department.id").getOrElse(20)
    put("departId", depId)
    put("departments", entityDao.search(OqlBuilder.from(classOf[Department], "dep").where("dep.teaching=true")))

    val evaquery = OqlBuilder.from(classOf[EvaluateResult], "evaluateR")
    evaquery.select("distinct evaluateR.clazz.semester.id")
    evaquery.where("evaluateR.teacher.user.department.id=:depId", depId)
    val semesterIds = entityDao.search(evaquery)
    val qur = OqlBuilder.from(classOf[Semester], "semester")
    qur.where("semester.beginOn<=:dat", LocalDate.now())

    val quetionQuery = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaireS")
    if (semesterIds.size > 0) {
      qur.where("semester.id in(:ids)", semesterIds)
      quetionQuery.where("questionnaireS.semester.id in(:semesterIds)", semesterIds)
    } else {
      qur.where("semester.id is null")
      quetionQuery.where("questionnaireS.semester.id is null")
    }
    quetionQuery.where("questionnaireS.teacher.user.department.id=:depId", depId)
    put("evaSemesters", entityDao.search(qur))
    quetionQuery.select("questionnaireS.semester.id,count(questionnaireS.teacher.id)")
    quetionQuery.groupBy("questionnaireS.semester.id")
    put("questionNums", entityDao.search(quetionQuery))
    val maps = Collections.newMap[String, Seq[TeacherEvalStat]]
    lis foreach { evaluationCriteriaItem =>
      val query = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaireStat")
      query.select("questionnaireStat.semester.id,count(questionnaireStat.teacher.id)")
      query.where("questionnaireStat.score>=" + evaluationCriteriaItem.minScore + " and questionnaireStat.score<" + evaluationCriteriaItem.maxScore)
      query.where("questionnaireStat.teacher.user.department.id=:depId", depId)
      query.groupBy("questionnaireStat.semester.id")
      maps.put(evaluationCriteriaItem.id.toString(), entityDao.search(query))
    }
    put("questionDeps", maps)
    forward()
  }

  /**
   * 历史评教统计
   */
  def historyCollegeStat(): View = {
    val lis = entityDao.search(OqlBuilder.from(classOf[EvaluationCriteriaItem], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) { redirect("search", "未找到评价标准！") }
    put("criterias", lis)
    val evaquery = OqlBuilder.from(classOf[EvaluateResult], "evaluateR")
    evaquery.select("distinct evaluateR.clazz.semester.id")
    val semesterIds = entityDao.search(evaquery)
    val qur = OqlBuilder.from(classOf[Semester], "semester")
    qur.where("semester.beginOn<=:dat", LocalDate.now())

    val quetionQuery = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaireS")
    if (semesterIds.size > 0) {
      qur.where("semester.id in(:ids)", semesterIds)
      quetionQuery.where("questionnaireS.semester.id in(:semesterIds)", semesterIds)
    } else {
      qur.where("semester.id is null")
      quetionQuery.where("questionnaireS.semester.id is null")
    }
    put("evaSemesters", entityDao.search(qur))
    quetionQuery.select("questionnaireS.semester.id,count(questionnaireS.teacher.id)")
    quetionQuery.groupBy("questionnaireS.semester.id")
    put("questionNums", entityDao.search(quetionQuery))
    val maps = Collections.newMap[String, Seq[TeacherEvalStat]]
    lis foreach { evaluationCriteriaItem =>
      //    for (EvaluationCriteriaItem evaluationCriteriaItem : lis) {
      val query = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaireStat")
      query.select("questionnaireStat.semester.id,count(questionnaireStat.teacher.id)")
      query.where("questionnaireStat.score>=" + evaluationCriteriaItem.minScore
        + " and questionnaireStat.score<" + evaluationCriteriaItem.maxScore)
      query.groupBy("questionnaireStat.semester.id")
      maps.put(evaluationCriteriaItem.id.toString(), entityDao.search(query))
    }
    put("questionDeps", maps)
    forward()
  }

  /**
   * 分项评教汇总
   */
  def collegeGroupItemInfo(): View = {
    val lis = entityDao.search(OqlBuilder.from(classOf[EvaluationCriteriaItem], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) { redirect("search", "未找到评价标准！") }
    put("criterias", lis)

    val evaquery = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaireStat")
    evaquery.where("questionnaireStat.semester.id=:semesId", getInt("semester.id").get)
    evaquery.join("questionnaireStat.questionTypeStats", "questionType")
    evaquery.select("distinct questionType.questionType.id")
    val queTypeIds = entityDao.search(evaquery)

    val quTqur = OqlBuilder.from(classOf[QuestionType], "questionType")
    val quetionQuery = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaireS")
    quetionQuery.join("questionnaireS.questionTypeStats", "questionTypeStat")
    if (queTypeIds.size > 0) {
      quTqur.where("questionType.id in(:ids)", queTypeIds)
      quetionQuery.where("questionTypeStat.questionType.id in(:queTypeIds)", queTypeIds)
    } else {
      quTqur.where("questionType.id is null")
      quetionQuery.where("questionTypeStat.questionType.id is null")
    }
    put("questionTypes", entityDao.search(quTqur))
    quetionQuery.select("questionTypeStat.questionType.id,count(questionnaireS.teacher.id)")
    quetionQuery.groupBy("questionTypeStat.questionType.id")
    put("quesTypeNums", entityDao.search(quetionQuery))

    val maps = Collections.newMap[String, Seq[TeacherEvalStat]]
    lis foreach { evaluationCriteriaItem =>
      val query = OqlBuilder.from(classOf[TeacherEvalStat], "questionnaireStat")
      query.where("questionnaireStat.semester.id=:semesId", getInt("semester.id").get)
      query.join("questionnaireStat.questionTypeStats", "questionTypeStat")
      query.select("questionTypeStat.questionType.id,count(questionnaireStat.teacher.id)")
      query.where("questionTypeStat.score>=" + evaluationCriteriaItem.minScore   + " and questionTypeStat.score<" + evaluationCriteriaItem.maxScore)
      query.groupBy("questionTypeStat.questionType.id")
      maps.put(evaluationCriteriaItem.id.toString(), entityDao.search(query))
    }
    put("questionDeps", maps)
    val que = OqlBuilder.from(classOf[EvaluateResult], "evaluateR")
    que.where("evaluateR.clazz.semester.id=:seiD", getInt("semester.id").get)
    // que.where("evaluateR.statType is 1")
    que.select("distinct evaluateR.teacher.id")
    val list = entityDao.search(que)
    put("persons", list.size)
    forward()
  }

}
