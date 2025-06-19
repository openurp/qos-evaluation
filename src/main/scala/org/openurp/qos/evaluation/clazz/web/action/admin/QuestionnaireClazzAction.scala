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
import org.beangle.commons.lang.Strings
import org.beangle.data.dao.{OqlBuilder, QueryBuilder}
import org.beangle.webmvc.view.View
import org.openurp.base.model.{Department, Project, Semester}
import org.openurp.code.edu.model.CourseType
import org.openurp.edu.clazz.model.Clazz
import org.openurp.qos.evaluation.app.course.service.StdEvaluateSwitchService
import org.openurp.qos.evaluation.clazz.model.QuestionnaireClazz
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction
import org.openurp.qos.evaluation.base.model.Questionnaire

import java.time.LocalDate

class QuestionnaireClazzAction extends ProjectRestfulAction[QuestionnaireClazz] {

  var evaluateSwitchService: StdEvaluateSwitchService = _

  override def indexSetting(): Unit = {
    given project: Project = getProject

    put("project", project)
    put("semester", getSemester)
    put("departments", project.departments)
    put("courseTypes", entityDao.getAll(classOf[CourseType]))
    put("questionnaires", entityDao.getAll(classOf[Questionnaire]))
  }

  override def search(): View = {
    val questionnaireId = getLong("questionnaire.id").getOrElse(-1)
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)

    // 检查时间
    val evaluateSwitch = evaluateSwitchService.getEvaluateSwitch(semester, getProject)
    if (null != evaluateSwitch) {
      //        && evaluateSwitch.checkOpen(new java.util.Date())) {
      put("isEvaluateSwitch", true)
    }
    val query = OqlBuilder.from(classOf[Questionnaire], "questionnaire")
    query.where(
      "questionnaire.beginOn <= :now and (questionnaire.endOn is null or questionnaire.endOn >= :now)",
      LocalDate.now)
    put("questionnaires", entityDao.search(query))
    // 判断(问卷是否存在)
    questionnaireId match {
      case 0 => //无问卷--教学任务list
        put("clazzs", entityDao.search(getQueryBuilderByClazz()))
        forward("clazzList")
      case -1 => //有问卷
        put("questionnaireClazzs", entityDao.search(getQueryBuilder))
        forward("list")
      case _ => //问卷Id
        put("questionnaireClazzs", entityDao.search(getQueryBuilder))
        forward("list")
    }
  }

  protected override def getQueryBuilder: OqlBuilder[QuestionnaireClazz] = {
    val query = OqlBuilder.from(classOf[QuestionnaireClazz], "questionnaireClazz")
    populateConditions(query)
    //    query.where(QueryHelper.extractConditions(classOf[Clazz], "clazz", null))
    //    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id") //.getOrElse(entityDao.search(semesterQuery).head.id)
    semesterId foreach { semesterId =>
      query.where("questionnaireClazz.clazz.semester.id = :semesterId", semesterId)
    }
    query.where("questionnaireClazz.clazz.project = :project", getProject)
    // 隐性条件(问卷类别,起始周期,上课人数)
    val questionnaireId = getLong("questionnaire.id").getOrElse(-1)
    val teacherName = get("teacher").orNull
    if (Strings.isNotBlank(teacherName)) {
      query.join("questionnaireClazz.clazz.teachers", "teacher")
      query.where("teacher.name like :teacherName", "%" + teacherName + "%")
    }
    if (questionnaireId != -1 && questionnaireId != 0) {
      query.where("questionnaireClazz.questionnaire.id =:questionnaireId", questionnaireId)

    }
    query.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    query
  }

  /**
   * 查询语句(教学任务,缺失问卷) 无问卷教学任务list
   *
   * @return
   */
  protected def getQueryBuilderByClazz(): QueryBuilder[Clazz] = {

    val query = OqlBuilder.from(classOf[Clazz], "clazz")
    populateConditions(query)
    query.where("clazz.project=:project", getProject)
    populateConditions(query)
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id") //.getOrElse(entityDao.search(semesterQuery).head.id)
    semesterId foreach { semesterId =>
      query.where("clazz.semester.id = :semesterId", semesterId)
    }
    val teacherName = get("teacher").getOrElse("")
    if (Strings.isNotBlank(teacherName)) {
      query.join("clazz.teachers", "teacher")
      query.where("teacher.name like :teacherName", "%" + teacherName + "%")
    }
    // 排除(已有问卷)
    query.where("not exists(from " + classOf[QuestionnaireClazz].getName + " questionnaireClazz"
      + " where questionnaireClazz.clazz = clazz)")
    query.orderBy(get(Order.OrderStr).orNull)
    query.limit(getPageLimit)
    query
  }

  /**
   * 设置(评教类型,定制问卷,条件-问卷缺失)
   *
   * @return
   */
  def saveQuestionnaireClazz(): View = {
    val isAll = get("isAll").get
    var clazzs: Seq[Clazz] = Seq()
    // 获取(更新对象)
    if ("all".equals(isAll)) {
      val qurey = getQueryBuilderByClazz()
      clazzs = entityDao.search(getQueryBuilderByClazz().limit(null))
    } else {
      val ids = getLongIds("clazz")
      clazzs = entityDao.find(classOf[Clazz], ids)
    }
    val questionnaireId = getLongId("questionnaire")
    if (questionnaireId != 0) {
      val isEvaluate = getBoolean("isEvaluate").get
      val questionnaire = entityDao.get(classOf[Questionnaire], questionnaireId)
      //        for (Clazz clazz : clazzs) {
      clazzs foreach { clazz =>
        val questionnaireClazz = new QuestionnaireClazz()
        questionnaireClazz.questionnaire = questionnaire
        questionnaireClazz.clazz = clazz
        questionnaireClazz.evaluateByTeacher = isEvaluate
        entityDao.saveOrUpdate(questionnaireClazz)
      }
    }
    redirect("search", "info.action.success")

  }

  /**
   * 设置(评教类型,定制问卷,条件-问卷存在)
   *
   * @return
   */
  def updateQuestionnaireClazz(): View = {
    val isAll = get("isAll").get
    var questionnaireClazzs: Seq[QuestionnaireClazz] = Seq()
    // 获取(更新对象)
    if ("all".equals(isAll)) {
      questionnaireClazzs = entityDao.getAll(classOf[QuestionnaireClazz])
    } else {
      val ids = getLongIds(simpleEntityName)
      questionnaireClazzs = entityDao.find(classOf[QuestionnaireClazz], ids)
    }

    val questionnaireId = this.getLong("questionnaire.id").getOrElse(0L)
    // 判断(是否删除)
    if (questionnaireId == 0L) {
      entityDao.remove(questionnaireClazzs)
    } else {
      val isEvaluate = getBoolean("isEvaluate").get
      val questionnaire = entityDao.get(classOf[Questionnaire], questionnaireId)
      //        for (QuestionnaireClazz questionnaireClazz : questionnaireClazzs) {
      questionnaireClazzs foreach { questionnaireClazz =>
        questionnaireClazz.questionnaire = questionnaire
        questionnaireClazz.evaluateByTeacher = isEvaluate

      }
      entityDao.saveOrUpdate(questionnaireClazzs)
    }
    redirect("search", "info.action.success")
  }
}
