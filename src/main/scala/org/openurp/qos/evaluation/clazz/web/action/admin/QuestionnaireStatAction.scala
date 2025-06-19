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
import org.beangle.webmvc.support.ServletSupport
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.ExportSupport
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Department, Project, Semester}
import org.openurp.code.edu.model.EducationLevel
import org.openurp.code.std.model.StdType
import org.openurp.qos.evaluation.clazz.model.{CourseEvalStat, CourseOptionStat, EvaluateResult, QuestionResult}
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction
import org.openurp.qos.evaluation.base.model.{AssessCriteria, AssessGrade, Indicator, Questionnaire}

import java.time.LocalDate

class QuestionnaireStatAction extends ProjectRestfulAction[CourseEvalStat], ExportSupport[CourseEvalStat], ServletSupport {

  override def index(): View = {
    given project: Project = getProject

    put("stdTypeList", project.stdTypes)
    put("departmentList", project.departments)

    var searchFormFlag = get("searchFormFlag").orNull
    if (searchFormFlag == null) {
      searchFormFlag = "beenStat"
    }
    put("searchFormFlag", searchFormFlag)
    put("departments", entityDao.getAll(classOf[Department]))
    val query = OqlBuilder.from(classOf[Questionnaire], "questionnaire")
    put("questionnaires", entityDao.search(query))
    put("currentSemester", this.getSemester)
    put("assessCriterias", entityDao.getAll(classOf[AssessCriteria]))
    put("indicators", entityDao.getAll(classOf[Indicator]))
    forward()
  }

  /**
   * 更新(教师)
   *
   * @return
   */
  def modifyTeacher(): View = {
    val quenStatId = getLongId("questionnaireStat")
    put("questionnaireStat", entityDao.get(classOf[CourseEvalStat], quenStatId))
    forward()
  }

  /**
   * 查询 (获得教师)
   */
  def searchTeacher(): Unit = {
    // throws IOException {
    val code = get("teacherCode").get
    val teachers = entityDao.search(OqlBuilder.from(classOf[Teacher], "sf").where("sf.code=:code", code))
    if (!teachers.isEmpty) {
      val teacher = teachers(0)
      response.setCharacterEncoding("utf-8")
      response.getWriter().print(
        teacher.id.toString + "_" + teacher.name + "_" + teacher.department.name)
    } else {
      response.getWriter().print("")
    }
  }

  /**
   * 保存(更新教师)
   *
   * @return
   */
  def saveTeacher(): View = {
    val questionnaireStat = entityDao.get(classOf[CourseEvalStat], getLong("questionnaireStat.id").get)
    questionnaireStat.teacher = entityDao.get(classOf[Teacher], getLong("teacher.id").get)
    entityDao.saveOrUpdate(questionnaireStat)
    redirect("search", "info.update.success")
  }

  override def remove(): View = {
    val idStr = get("questionnaireStat.ids").get
    val Ids = idStr.split(",")
    val questionSIds = Array[Long]()
    for (i <- 0 to Ids.length) {
      questionSIds(i) = Ids(i).toLong
    }
    val query = OqlBuilder.from(classOf[CourseEvalStat], "questionS")
    query.where("questionS.id in(:ids)", questionSIds)
    val li = entityDao.search(query)
    try {
      li foreach { questionnaireStat =>

        if (questionnaireStat.questionStats.size > 0) {
          val questionStats = questionnaireStat.questionStats

          questionStats foreach { questionstat =>

            val options = entityDao.search(OqlBuilder.from(classOf[CourseOptionStat], "op").where("op.questionStat.id=:id", questionstat.id))
            val optionStats = Collections.newBuffer[CourseOptionStat]

            options foreach { optionStat =>

              questionstat.optionStats -= optionStat
              optionStats += optionStat
            }
            if (optionStats.size > 0) {
              entityDao.remove(optionStats)
            }
            questionnaireStat.questionStats -= questionstat
            entityDao.remove(questionstat)

          }

        }
        if (questionnaireStat.indicatorStats.size > 0) {
          val questionTS = questionnaireStat.indicatorStats
          questionnaireStat.indicatorStats --= questionTS
          entityDao.remove(questionTS)
        }
        entityDao.remove(questionnaireStat)

      }
    } catch {
      case e: Exception =>
        // TODO: handle exception
        redirect("search", "删除失败！")
    }
    redirect("search", "info.delete.success")
  }

  /**
   * 跳转(统计首页面)
   */
  def statHome(): View = {
    put("stdTypeList", entityDao.getAll(classOf[StdType]))
    put("departmentList", entityDao.search(OqlBuilder.from(classOf[Department], "de").where("de.teaching=:tea", true)))
    put("semester", 20141)
    put("educations", entityDao.getAll(classOf[EducationLevel]))
    put("departments", entityDao.search(OqlBuilder.from(classOf[Department], "de").where("de.teaching=:tea", true)))
    forward()
  }

  /**
   * 跳转(初始有效值页面)
   */
  def initValidHome(): View = {
    put("stdTypeList", entityDao.getAll(classOf[StdType]))
    put("departmentList", entityDao.search(OqlBuilder.from(classOf[Department], "de").where("de.teaching=:tea", true)))
    put("semester", 20141)
    forward()
  }

  /**
   * 跳转(院系评教比较,根据所属部门)
   *
   * @return
   */
  def departDistributeStat(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val lis = entityDao.search(OqlBuilder.from(classOf[AssessGrade], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) {
      redirect("search", "未找到评价标准！");
    }
    put("criterias", lis)
    put("departments", entityDao.search(OqlBuilder.from(classOf[Department], "dep").where("dep.teaching=:tea", true)))
    put("semester", entityDao.get(classOf[Semester], semesterId))
    val que = OqlBuilder.from[Double](classOf[EvaluateResult].getName + " evaluateResult," + classOf[QuestionResult].getName + " questionResult")
    que.select("sum(questionResult.score)/count(distinct evaluateResult.id)")
    que.where("evaluateResult.id=questionResult.result.id")
    que.where("evaluateResult.clazz.semester.id=" + semesterId)
    val lit = entityDao.search(que)
    var fl = 0d
    if (lit.size > 0) {
      if (lit(0) != 0f) {
        fl = lit(0)
      }
    }
    put("evaluateResults", fl)
    val hql = OqlBuilder.from(classOf[CourseEvalStat], "evaluateR")
    hql.select("evaluateR.clazz.teachDepart.id,count( evaluateR.teacher.id)")
    hql.where("evaluateR.clazz.semester.id=" + semesterId)
    hql.groupBy("evaluateR.clazz.teachDepart.id,evaluateR.clazz.semester.id")
    put("questionNums", entityDao.search(hql))
    val maps = Collections.newMap[String, Seq[Array[Any]]]
    lis foreach { assessGrade =>
      val query = OqlBuilder.from[Array[Any]](classOf[CourseEvalStat].getName, "questionnaireStat")
      query.where("questionnaireStat.semester.id=:semesterId", semesterId)
      query.select("questionnaireStat.clazz.teachDepart.id,count(questionnaireStat.teacher.id)")
      query.where("questionnaireStat.score>=" + assessGrade.minScore
        + " and questionnaireStat.score<" + assessGrade.maxScore)
      query.groupBy("questionnaireStat.clazz.teachDepart.id")
      maps.put(assessGrade.id.toString(), entityDao.search(query))

    }
    put("questionDeps", maps)
    forward()
  }

}
