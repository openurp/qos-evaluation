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

package org.openurp.qos.evaluation.questionnaire.web.action

import org.beangle.commons.collection.Order
import org.beangle.commons.lang.Strings
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.base.model.{Department, Project}
import org.openurp.qos.evaluation.base.model.{OptionGroup, Question, Questionnaire}
import org.openurp.qos.evaluation.questionnaire.service.IndicatorService
import org.openurp.starter.web.support.ProjectSupport

import java.time.{Instant, LocalDate}

/**
 * 问题维护响应类
 *
 * @author chaostone
 */
class QuestionAction extends RestfulAction[Question] with ProjectSupport {

  var indicatorService: IndicatorService = _

  override def search(): View = {
    val builder = OqlBuilder.from(classOf[Question], "question")
    populateConditions(builder)
    builder.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    val questions = entityDao.search(builder)

    put("questions", questions)
    forward()
  }

  protected override def editSetting(entity: Question): Unit = {
    val optionGroups = entityDao.getAll(classOf[OptionGroup])
    put("optionGroups", optionGroups)
    val departmentList = entityDao.getAll(classOf[Department])
    val indicators = indicatorService.getIndicators()
    put("indicators", indicators)
    put("departmentList", departmentList)
  }

  protected override def saveAndRedirect(entity: Question): View = {
    try {
      val question = entity.asInstanceOf[Question]
      question.project = getProject
      question.updatedAt = Instant.now
      val content = question.contents
      question.beginOn = LocalDate.parse(get("question.beginOn").get)
      question.endOn = get("question.endOn").filter(Strings.isNotEmpty(_)).map(LocalDate.parse(_))
      question.contents = content.replaceAll("<", "&#60;").replaceAll(">", "&#62;")
      question.updatedAt = Instant.now
      entityDao.saveOrUpdate(question)
      redirect("search", "info.save.success")
    } catch {
      case e: Exception =>
        logger.info("saveAndForwad failure", e)
        redirect("search", "info.save.failure")
    }
  }

  override def remove(): View = {
    val questionIds = getLongId("question")
    val questions = entityDao.get(classOf[Question], questionIds)

    val query = OqlBuilder.from(classOf[Questionnaire], "questionnaire")
    query.join("questionnaire.questions", "question")
    query.where("question in (:questions)", questions)
    val questionnaires = entityDao.search(query)
    if (!questionnaires.isEmpty) {
      redirect("search", "删除失败,选择的数据中已有被评教问卷引用");
    }
    entityDao.remove(questions)
    redirect("search", "删除成功")
  }

}
