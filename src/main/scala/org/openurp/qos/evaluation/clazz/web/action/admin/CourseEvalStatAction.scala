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

import org.beangle.commons.collection.{Collections, Order}
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.view.View
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Department, Project, Semester}
import org.openurp.code.edu.model.EducationLevel
import org.openurp.edu.clazz.model.Clazz
import org.openurp.qos.evaluation.app.course.service.Ranker
import org.openurp.qos.evaluation.base.model.*
import org.openurp.qos.evaluation.clazz.model.*

import java.time.{Instant, LocalDate}
import scala.collection.mutable.{Buffer, ListBuffer}

class CourseEvalStatAction extends ProjectRestfulAction[CourseEvalStat] {

  override def index(): View = {
    given project: Project = getProject

    put("project", project)
    var searchFormFlag = get("searchFormFlag").orNull
    if (searchFormFlag == null) {
      searchFormFlag = "beenStat"
    }
    put("searchFormFlag", searchFormFlag)
    put("departments", findInSchool(classOf[Department]))
    val query = OqlBuilder.from(classOf[Questionnaire], "questionnaire")
    put("questionnaires", entityDao.search(query))
    put("currentSemester", getSemester)
    forward()
  }

  override def search(): View = {
    val semesterId = getInt("semester.id").get
    val semester = entityDao.get(classOf[Semester], semesterId)
    val courseEvalStat = OqlBuilder.from(classOf[CourseEvalStat], "courseEvalStat")
    populateConditions(courseEvalStat)
    courseEvalStat.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    courseEvalStat.where("courseEvalStat.semester=:semester", semester)
    put("courseEvalStats", entityDao.search(courseEvalStat))
    forward()
  }

  override def remove(): View = {
    val questionSIds = getLongIds("courseEvalStat")
    val query = OqlBuilder.from(classOf[CourseEvalStat], "questionS")
    query.where("questionS.id in(:ids)", questionSIds)
    entityDao.remove(entityDao.search(query))
    redirect("search", "info.remove.success")
  }

  /**
   * 清除统计数据
   */
  private def removeStats(project: Project, semesterId: Int): Unit = {
    val query = OqlBuilder.from(classOf[CourseEvalStat], "les")
    query.where("les.clazz.semester.id=:semesterId", semesterId)
    query.where("les.clazz.project=:project", project)
    entityDao.remove(entityDao.search(query))
  }

  /**
   * 院系历史评教
   */
  def depHistoryStat(): View = {
    val lis = entityDao.search(OqlBuilder.from(classOf[AssessGrade], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) {
      redirect("search", "未找到评价标准！")
    }
    put("criterias", lis)
    val depId = getInt("department.id").getOrElse(20)
    put("departId", depId)
    put("departments", entityDao.search(OqlBuilder.from(classOf[Department], "dep").where("dep.teaching=true")))
    val evaquery = OqlBuilder.from(classOf[CourseEvalStat], "stat")
    evaquery.select("distinct stat.semester.id")
    evaquery.where("stat.teachDepart.id=:depId", depId)
    val semesterIds = entityDao.search(evaquery)
    val qur = OqlBuilder.from(classOf[Semester], "semester")
    qur.where("semester.beginOn<=:dat", LocalDate.now())
    val quetionQuery = OqlBuilder.from(classOf[CourseEvalStat], "questionnaireS")
    if (semesterIds.size > 0) {
      qur.where("semester.id in(:ids)", semesterIds)
      quetionQuery.where("questionnaireS.semester.id in(:semesterIds)", semesterIds)
    } else {
      qur.where("semester.id is null")
      quetionQuery.where("questionnaireS.semester.id is null")
    }
    quetionQuery.where("questionnaireS.teachDepart.id=:depId", depId)
    put("evaSemesters", entityDao.search(qur))
    quetionQuery.select("questionnaireS.semester.id,count(questionnaireS.teacher.id)")
    quetionQuery.groupBy("questionnaireS.semester.id")
    put("questionNums", entityDao.search(quetionQuery))
    val maps = Collections.newMap[String, Seq[CourseEvalStat]]
    lis foreach { assessGrade =>
      val query = OqlBuilder.from(classOf[CourseEvalStat], "questionnaireStat")
      query.select("questionnaireStat.semester.id,count(questionnaireStat.teacher.id)")
      query.where("questionnaireStat.score>=" + assessGrade.minScore + " and questionnaireStat.score<" + assessGrade.maxScore)
      query.where("questionnaireStat.teachDepart.id=:depId", depId)
      query.groupBy("questionnaireStat.semester.id")
      maps.put(assessGrade.id.toString(), entityDao.search(query))
    }
    put("questionDeps", maps)
    forward()
  }

  /**
   * 院系评教统计
   */
  def departmentChoiceConfig(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val lis = entityDao.search(OqlBuilder.from(classOf[AssessGrade], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) {
      redirect("search", "未找到评价标准！")
    }
    put("criterias", lis)
    put("departments", entityDao.search(OqlBuilder.from(classOf[Department], "depart").where("depart.teaching =:teaching", true)))
    put("semester", semester)
    val que = OqlBuilder.from[Any](classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult")
    que.select("sum(questionResult.score)/count(distinct evaluateResult.id)")
    que.where("evaluateResult.id=questionResult.result.id")
    que.where("evaluateResult.clazz.semester.id=" + semesterId)
    val lit = entityDao.search(que)
    var fl = 0f
    if (lit.size > 0) {
      if (lit(0) != null) {
        fl = lit(0).toString().toFloat
      }
    }
    put("evaluateResults", fl)
    val query = OqlBuilder.from(classOf[CourseEvalStat], "evaluateR")
    query.select("evaluateR.teachDepart.id,count( evaluateR.teacher.id)")
    query.where("evaluateR.semester.id =:semesterId ", semesterId)
    query.groupBy("evaluateR.teachDepart.id,evaluateR.semester.id")
    put("questionNums", entityDao.search(query))
    val maps = Collections.newMap[String, Seq[CourseEvalStat]]
    lis foreach { assessGrade =>
      val query = OqlBuilder.from(classOf[CourseEvalStat], "questionnaireStat")
      query.where("questionnaireStat.semester.id=:semesterId", semesterId)
      query.select("questionnaireStat.teachDepart.id,count(questionnaireStat.teacher.id)")
      query.where("questionnaireStat.score>=" + assessGrade.minScore
        + " and questionnaireStat.score<" + assessGrade.maxScore)
      query.groupBy("questionnaireStat.teachDepart.id")
      maps.put(assessGrade.id.toString(), entityDao.search(query))
    }
    put("questionDeps", maps)
    forward()
  }

  /**
   * 历史评教统计
   */
  def historyCollegeStat(): View = {
    val lis = entityDao.search(OqlBuilder.from(classOf[AssessGrade], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) {
      redirect("search", "未找到评价标准！")
    }
    put("criterias", lis)
    val evaquery = OqlBuilder.from(classOf[EvaluateResult], "evaluateR")
    evaquery.select("distinct evaluateR.clazz.semester.id")
    val semesterIds = entityDao.search(evaquery)
    val qur = OqlBuilder.from(classOf[Semester], "semester")
    qur.where("semester.beginOn<=:dat", LocalDate.now())
    val quetionQuery = OqlBuilder.from(classOf[CourseEvalStat], "questionnaireS")
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
    val maps = Collections.newMap[String, Seq[CourseEvalStat]]
    lis foreach { assessGrade =>
      //    for (AssessGrade assessGrade : lis) {
      val query = OqlBuilder.from(classOf[CourseEvalStat], "questionnaireStat")
      query.select("questionnaireStat.semester.id,count(questionnaireStat.teacher.id)")
      query.where("questionnaireStat.score>=" + assessGrade.minScore
        + " and questionnaireStat.score<" + assessGrade.maxScore)
      query.groupBy("questionnaireStat.semester.id")
      maps.put(assessGrade.id.toString(), entityDao.search(query))
    }
    put("questionDeps", maps)
    forward()
  }

  /**
   * 分项评教汇总
   */
  def collegeGroupItemInfo(): View = {
    val lis = entityDao.search(OqlBuilder.from(classOf[AssessGrade], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) {
      redirect("search", "未找到评价标准！")
    }
    put("criterias", lis)

    val evaquery = OqlBuilder.from(classOf[CourseEvalStat], "stat")
    evaquery.where("stat.semester.id=:semesId", getInt("semester.id").get)
    evaquery.join("stat.indicatorStats", "indicator")
    evaquery.select("distinct indicator.indicator.id")
    val queTypeIds = entityDao.search(evaquery)

    val quTqur = OqlBuilder.from(classOf[Indicator], "indicator")
    val quetionQuery = OqlBuilder.from(classOf[CourseEvalStat], "questionnaireS")
    quetionQuery.join("questionnaireS.indicatorStats", "indicatorStat")
    if (queTypeIds.size > 0) {
      quTqur.where("indicator.id in(:ids)", queTypeIds)
      quetionQuery.where("indicatorStat.indicator.id in(:queTypeIds)", queTypeIds)
    } else {
      quTqur.where("indicator.id is null")
      quetionQuery.where("indicatorStat.indicator.id is null")
    }
    put("indicators", entityDao.search(quTqur))
    quetionQuery.select("indicatorStat.indicator.id,count(questionnaireS.teacher.id)")
    quetionQuery.groupBy("indicatorStat.indicator.id")
    put("quesTypeNums", entityDao.search(quetionQuery))

    val maps = Collections.newMap[String, Seq[CourseEvalStat]]
    lis foreach { assessGrade =>
      val query = OqlBuilder.from(classOf[CourseEvalStat], "questionnaireStat")
      query.where("questionnaireStat.clazz.semester.id=:semesId", getInt("semester.id").get)
      query.join("questionnaireStat.indicatorStats", "indicatorStat")
      query.select("indicatorStat.indicator.id,count(questionnaireStat.teacher.id)")
      query.where("indicatorStat.score>=" + assessGrade.minScore + " and indicatorStat.score<" + assessGrade.maxScore)
      query.groupBy("indicatorStat.indicator.id")
      maps.put(assessGrade.id.toString(), entityDao.search(query))
    }
    put("questionDeps", maps)
    val que = OqlBuilder.from(classOf[EvaluateResult], "evaluateR")
    que.where("evaluateR.clazz.semester.id=:seiD", getInt("semester.id").get)
    que.where("evaluateR.statType is 1")
    que.select("distinct evaluateR.teacher.id")
    val list = entityDao.search(que)
    put("persons", list.size)
    //    put("indicators", entityDao.getAll(classOf[Indicator]))
    forward()
  }

  /**
   * 教师历史评教
   */
  def evaluateTeachHistory(): View = {
    val id = getLong("courseEvalStat.id").get
    val questionnaires = entityDao.get(classOf[CourseEvalStat], id)
    val query = OqlBuilder.from(classOf[CourseEvalStat], "questionnaires")
    query.where("questionnaires.teacher.id=:teaIds", questionnaires.teacher.id)
    query.orderBy("questionnaires.semester.beginOn")
    put("teacher", questionnaires.teacher)
    put("teachEvaluates", entityDao.search(query))
    forward()
  }

  def teachQuestionDetailStat(): View = {
    put("questionnaires", entityDao.get(classOf[CourseEvalStat], getLong("questionnaireStat.id").get))
    forward()
  }

  /**
   * 跳转(统计首页面)
   */
  def statHome(): View = {
    given project: Project = getProject

    put("stdTypeList", project.stdTypes)
    put("departmentList", project.departments)

    put("educations", project.levels)
    val teachingDeparts = entityDao.search(OqlBuilder.from(classOf[Department], "depart").where("depart.teaching =:tea", true))
    put("departments", teachingDeparts)

    put("currentSemester", getSemester)
    forward()
  }

  /**
   * 跳转(初始有效值页面)
   */
  def initValidHome(): View = {
    forward()
  }

  /**
   * 设置有效记录
   */
  def setValid(): View = {
    forward()
  }

  def rankStat(): View = {
    val semesterId = getInt("semester.id").getOrElse(0)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val project = this.getProject
    //    排名
    val rankQuery = OqlBuilder.from(classOf[CourseEvalStat], "les")
    rankQuery.where("les.clazz.semester.id=:semesterId", semesterId)
    rankQuery.where("les.clazz.project=:project", project)
    val evals = entityDao.search(rankQuery)
    Ranker.over(evals) { (x, r) =>
      x.categoryRank = r
    }
    val departEvalMaps = evals.groupBy(x => x.teachDepart)
    departEvalMaps.values foreach { departEvals =>
      Ranker.over(departEvals) { (x, r) =>
        x.departRank = r
      }
    }
    entityDao.saveOrUpdate(evals)
    redirect("index", "info.action.success")
  }

  /**
   * 统计(任务评教结果)
   * FIXME 去除最高5%和最低分数5%,以及人数少于15人的评教结果,  也没有正确计算问卷总数（只是将有效问卷和问卷总数相等）
   *
   * @return
   */
  def stat(): View = {
    val semesterId = getInt("semester.id").getOrElse(0)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val project = this.getProject
    // 删除历史统计数据
    removeStats(project, semesterId)
    // teacher、clazz、question问题得分统计
    val que = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    que.where("questionR.result.clazz.project=:project", project)
    que.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    que.where("questionR.result.statType is 1")
    que.select("questionR.result.teacher.id,questionR.result.clazz.id,questionR.question.id,sum(questionR.score),avg(questionR.score)")
    que.groupBy("questionR.result.teacher.id,questionR.result.clazz.id,questionR.question.id")
    val wtStatMap = new collection.mutable.HashMap[Tuple2[Any, Any], Buffer[Tuple3[Long, Number, Number]]]
    val rs2 = entityDao.search(que)
    rs2 foreach { a =>
      val buffer = wtStatMap.getOrElseUpdate((a(0), a(1)), new ListBuffer[Tuple3[Long, Number, Number]])
      buffer += Tuple3(a(2).asInstanceOf[Long], a(3).asInstanceOf[Number], a(4).asInstanceOf[Number])
    }
    // 问卷得分统计
    val quer = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    quer.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    quer.where("questionR.result.clazz.project=:project", project)
    quer.where("questionR.result.statType is 1")
    quer.select("questionR.result.clazz.id,questionR.result.teacher.id,questionR.result.questionnaire.id,"
      + "sum(questionR.score),sum(questionR.score)/count(distinct questionR.result.id),count(distinct questionR.result.id)")
    quer.groupBy("questionR.result.clazz.id,questionR.result.teacher.id,questionR.result.questionnaire.id")

    val wjStat = entityDao.search(quer)
    // 问题类别统计
    val tyquery = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    tyquery.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    tyquery.where("questionR.result.clazz.project=:project", project)
    tyquery.where("questionR.result.statType is 1")
    tyquery.where("questionR.result.teacher is not null")
    tyquery.select("questionR.result.clazz.id,questionR.result.teacher.id,questionR.question.indicator.id,sum(questionR.score),sum(questionR.score)/count(distinct questionR.result.id)")
    tyquery.groupBy("questionR.result.clazz.id,questionR.result.teacher.id,questionR.question.indicator.id")

    val typeStatMap = new collection.mutable.HashMap[Tuple2[Any, Any], Buffer[Tuple3[Long, Number, Number]]]
    entityDao.search(tyquery) foreach { a =>
      val buffer = typeStatMap.getOrElseUpdate((a(0), a(1)), new ListBuffer[Tuple3[Long, Number, Number]])
      buffer += Tuple3(a(2).asInstanceOf[Long], a(3).asInstanceOf[Number], a(4).asInstanceOf[Number])
    }
    // 选项统计
    val opQuery = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR")
    opQuery.where("questionR.result.clazz.semester.id=:semesterId", semesterId)
    opQuery.where("questionR.result.clazz.project=:project", project)
    opQuery.where("questionR.result.statType is 1")
    opQuery.select("questionR.result.clazz.id," + "questionR.result.teacher.id,questionR.question.id,questionR.option.id,count(questionR.id)")
    opQuery.groupBy("questionR.result.clazz.id,questionR.result.teacher.id,questionR.question.id,questionR.option.id")
    val optionStatMap = new collection.mutable.HashMap[Tuple3[Any, Any, Any], Buffer[Tuple2[Long, Number]]]
    entityDao.search(opQuery) foreach { a =>
      val buffer = optionStatMap.getOrElseUpdate((a(0), a(1), a(2)), new ListBuffer[Tuple2[Long, Number]])
      buffer += Tuple2(a(3).asInstanceOf[Long], a(4).asInstanceOf[Number])
    }

    val questionMap = entityDao.getAll(classOf[Question]).map(o => (o.id, o)).toMap
    val questiontyMap = entityDao.getAll(classOf[Indicator]).map(o => (o.id, o)).toMap
    val optionMap = entityDao.getAll(classOf[Option]).map(o => (o.id, o)).toMap

    //任务问卷得分统计
    wjStat foreach { evaObject =>
      val questionS = new CourseEvalStat
      val clazz = entityDao.get(classOf[Clazz], evaObject(0).asInstanceOf[Long])
      questionS.crn = Some(clazz.crn)
      questionS.course = clazz.course
      questionS.semester = clazz.semester
      questionS.project = clazz.project
      questionS.teacher = new Teacher()
      questionS.teacher.id = evaObject(1).asInstanceOf[Long]
      questionS.updatedAt = Instant.now

      val avgScore = evaObject(4).toString().toFloat
      questionS.score = (Math.round(avgScore * 100) * 1.0 / 100).floatValue
      questionS.tickets = Integer.valueOf(evaObject(5).toString())
      // 添加问题得分统计
      val questionDetailStats = Collections.newBuffer[CourseQuestionStat]
      wtStatMap.get((questionS.teacher.id, clazz.id)) foreach { buffer =>
        buffer foreach { wt =>
          val detailStat = new CourseQuestionStat
          // 添加问题
          detailStat.question = questionMap(wt._1)
          detailStat.score = wt._3.toString().toFloat
          detailStat.stat = questionS

          // 添加选项统计
          val optionStates = Collections.newBuffer[CourseOptionStat]
          optionStatMap.get((clazz.id, questionS.teacher.id, detailStat.question.id)) foreach { buffer =>
            buffer foreach { os =>
              val optionstat = new CourseOptionStat
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
      val indicatorStats = Collections.newBuffer[CourseIndicatorStat]
      typeStatMap.get((clazz.id, questionS.teacher.id)) foreach { buffer =>
        buffer foreach { os =>
          val questionTs = new CourseIndicatorStat
          questionTs.score = os._3.floatValue
          questionTs.stat = questionS
          questionTs.indicator = questiontyMap(os._1)
          indicatorStats += questionTs
        }
      }
      questionS.indicatorStats = indicatorStats
      entityDao.saveOrUpdate(questionS)
    }
    //    排名
    val rankQuery = OqlBuilder.from(classOf[CourseEvalStat], "les")
    rankQuery.where("les.clazz.semester.id=:semesterId", semesterId)
    rankQuery.where("les.clazz.project=:project", project)
    val evals = entityDao.search(rankQuery)
    Ranker.over(evals) { (x, r) =>
      x.categoryRank = r
    }
    val departEvalMaps = evals.groupBy(x => x.teachDepart)
    departEvalMaps.values foreach { departEvals =>
      Ranker.over(departEvals) { (x, r) =>
        x.departRank = r
      }
    }
    entityDao.saveOrUpdate(evals)
    redirect("index", "info.action.success")
  }

}
