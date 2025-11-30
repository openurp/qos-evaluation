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

import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.view.View
import org.openurp.base.model.{Project, Semester}
import org.openurp.edu.clazz.model.Clazz
import org.openurp.qos.evaluation.clazz.config.StdEvaluateSwitch
import org.openurp.qos.evaluation.clazz.model.QuestionnaireClazz
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction

class StdEvaluateSwitchAction extends ProjectRestfulAction[StdEvaluateSwitch] {

  override def search(): View = {
    val opened = getBoolean("evaluateSwitch.opened")
    val semesterId = getInt("evaluateSwitch.semester.id")
    val queryQuestionnaire = OqlBuilder.from[Array[Any]](classOf[QuestionnaireClazz].getName, "questionnaireClazz")
    semesterId.foreach { semesterId => queryQuestionnaire.where("questionnaireClazz.clazz.semester.id =:semesterId", semesterId) }
    queryQuestionnaire.where("questionnaireClazz.clazz.project =:project", getProject)
    queryQuestionnaire.groupBy("questionnaireClazz.clazz.semester.id ")
    queryQuestionnaire.select("questionnaireClazz.clazz.semester.id,count(*)")
    val countMap = entityDao.search(queryQuestionnaire).map(a => (a(0).asInstanceOf[Int], a(1).asInstanceOf[Number])).toMap
    put("countMap", countMap)
    val queryClazz = OqlBuilder.from[Array[Any]](classOf[Clazz].getName, "clazz")
    semesterId.foreach { semesterId => queryClazz.where("clazz.semester.id =:semesterId", semesterId) }
    queryClazz.where("clazz.project =:project", getProject)
    // 排除(已有问卷)
    queryClazz.where("not exists(from " + classOf[QuestionnaireClazz].getName + " questionnaireClazz"
      + " where questionnaireClazz.clazz = clazz)")
    queryClazz.groupBy("clazz.semester.id")
    queryClazz.select("clazz.semester.id, count(*)")
    val clazzCountMap = entityDao.search(queryClazz).map(a => (a(0).asInstanceOf[Int], a(1).asInstanceOf[Number])).toMap
    put("clazzCountMap", clazzCountMap)
    val stdEvaluateSwitchs = getQueryBuilder
    semesterId.foreach { semesterId => stdEvaluateSwitchs.where("stdEvaluateSwitch.semester.id=:semesterId", semesterId) }
    stdEvaluateSwitchs.where("stdEvaluateSwitch.project=:project", getProject)
    opened.foreach { opened => stdEvaluateSwitchs.where("stdEvaluateSwitch.opened=:opened", opened) }
    put("stdEvaluateSwitchs", entityDao.search(stdEvaluateSwitchs))
    forward()
  }

  override def editSetting(entity: StdEvaluateSwitch): Unit = {
    val project = getProject
    val query = OqlBuilder.from(classOf[Semester], "s").where("s.calendar =:calendar ", project.calendar)
    put("semesters", entityDao.search(query))
    put("project", project)
  }

  override def saveAndRedirect(evaluateSwitch: StdEvaluateSwitch): View = {
    if (!evaluateSwitch.persisted) {
      val query = OqlBuilder.from(classOf[StdEvaluateSwitch], "evaluateSwitch")
      query.where("evaluateSwitch.semester.id =:semesterId", evaluateSwitch.semester.id)
      query.where("evaluateSwitch.project.id =:project", evaluateSwitch.project.id)
      val evaluateSwitchs = entityDao.search(query)
      if (!evaluateSwitchs.isEmpty) {
        return redirect("search", "该学期评教开关已存在,请删除后再新增!", "&evaluateSwitch.project.id=" + evaluateSwitch.project.id + "&evaluateSwitch.semester.id=" + evaluateSwitch.semester.id)
      }
    }
    try {
      saveOrUpdate(evaluateSwitch)
      redirect("search", "info.save.success", "success,&evaluateSwitch.project.id=" + evaluateSwitch.project.id + "&evaluateSwitch.semester.id=" + evaluateSwitch.semester.id)
    } catch {
      case e: Exception =>
        redirect("search", "info.save.failure", "failure,&evaluateSwitch.project.id=" + evaluateSwitch.project.id + "&evaluateSwitch.semester.id=" + evaluateSwitch.semester.id)
    }
  }

  protected override def indexSetting(): Unit = {
    given project: Project = getProject

    put("project", project)
    put("currentSemester", getSemester)
  }

}
