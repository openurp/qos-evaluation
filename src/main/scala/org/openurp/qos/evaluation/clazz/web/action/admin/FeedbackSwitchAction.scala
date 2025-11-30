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
import org.openurp.base.model.Project
import org.openurp.qos.evaluation.clazz.config.FeedbackSwitch
import org.openurp.qos.evaluation.clazz.web.action.admin.ProjectRestfulAction

class FeedbackSwitchAction extends ProjectRestfulAction[FeedbackSwitch] {

  protected override def indexSetting(): Unit = {
    given project: Project = getProject

    put("currentSemester", getSemester)
  }

  override def search(): View = {
    val opened = getBoolean("opened")
    val semesterId = getInt("semester.id")
    val textEvaluationSwitchs = OqlBuilder.from(classOf[FeedbackSwitch], "feedbackSwitch")
    semesterId.foreach { semesterId => textEvaluationSwitchs.where("feedbackSwitch.semester.id=:semesterId", semesterId) }
    textEvaluationSwitchs.where("feedbackSwitch.project=:project", getProject)
    put("textEvaluationSwitchs", entityDao.search(textEvaluationSwitchs))
    forward()
  }

  override def saveAndRedirect(evaluateSwitch: FeedbackSwitch): View = {
    if (!evaluateSwitch.persisted) {
      val query = OqlBuilder.from(classOf[FeedbackSwitch], "feedbackSwitch")
      query.where("feedbackSwitch.semester.id =:semesterId", evaluateSwitch.semester.id)
      val feedbackSwitchs = entityDao.search(query)
      if (!feedbackSwitchs.isEmpty) {
        return redirect("search", "&feedbackSwitch.semester.id=" + evaluateSwitch.semester.id, "该学期评教开关已存在,请删除后再新增!")
      }
    }
    try {
      saveOrUpdate(evaluateSwitch)
      redirect("search", "success ,&evaluateSwitch.semester.id=" + evaluateSwitch.semester.id, "info.save.success")
    } catch {
      case e: Exception =>
        e.printStackTrace()
        redirect("search", "failure,&evaluateSwitch.semester.id=" + evaluateSwitch.semester.id, "info.save.failure")
    }
  }

}
