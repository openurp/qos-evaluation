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

import org.beangle.commons.collection.{Collections, Order}
import org.beangle.commons.lang.{Numbers, Strings}
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.annotation.param
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.qos.evaluation.base.model.{Indicator, Question, Questionnaire}
import org.openurp.qos.evaluation.clazz.model.QuestionnaireClazz
import org.openurp.starter.web.support.ProjectSupport

import java.time.{Instant, LocalDate}
import scala.collection.mutable.Buffer

class QuestionnaireAction extends RestfulAction[Questionnaire] with ProjectSupport {

  override def search(): View = {
    val builder = OqlBuilder.from(classOf[Questionnaire], "questionnaire")
    populateConditions(builder)
    builder.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    val questionnaires = entityDao.search(builder)

    put("questionnaires", questionnaires)
    forward()
  }

  override def editSetting(questionnaire: Questionnaire): Unit = {
    val questionTree = questionnaire.questions.groupBy(x => x.indicator)
    put("questions", questionnaire.questions)
    put("questionTree", questionTree)

  }

  override def info(@param("id") id: String): View = {
    if (Strings.isBlank(id)) {
      logger.info("查看失败")
      redirect("search", "请选择一条记录")
    }
    val questionnaire = entityDao.get(classOf[Questionnaire], Numbers.toLong(id))
    val questionTree = Collections.newMap[Indicator, Buffer[Question]]
    questionnaire.questions foreach { question =>
      val key = question.indicator
      var questions: Buffer[Question] = questionTree.get(key).orNull
      if (null == questions) {
        questions = Collections.newBuffer
      }
      questions += question
      questions.sortWith((x, y) => x.priority > y.priority)
      questionTree.put(key, questions)
    }
    put("questionTree", questionTree)
    put("questionnaire", questionnaire)
    forward()
  }

  override def saveAndRedirect(entity: Questionnaire): View = {
    val questionnaire = entity.asInstanceOf[Questionnaire]
    questionnaire.beginOn = LocalDate.parse(get("questionnaire.beginOn").get)
    questionnaire.updatedAt = Instant.now
    if (null == questionnaire.project) {
      questionnaire.project = getProject
    }
    questionnaire.endOn = get("questionnaire.endOn").filter(Strings.isNotBlank(_)).map(LocalDate.parse(_))
    questionnaire.questions.clear()
    questionnaire.questions ++= entityDao.find(classOf[Question], getLongIds("questionnaire.question"))

    entityDao.saveOrUpdate(questionnaire)
    redirect("search", "info.save.success")
  }

  override def remove(): View = {
    val questionnaireIds = getLongIds("questionnaire")
    val query1 = OqlBuilder.from(classOf[Questionnaire], "questionnaire")
    query1.where("questionnaire.id in (:questionnaireIds)", questionnaireIds)
    val questionnaires = entityDao.search(query1)
    val query = OqlBuilder.from(classOf[QuestionnaireClazz], "ql")
    query.where("ql.questionnaire in (:questionnaires)", questionnaires)
    val qls = entityDao.search(query)
    if (!qls.isEmpty) {
      return redirect("search", "删除失败,选择的数据中已有被课程问卷引用");
    }

    entityDao.remove(questionnaires)
    return redirect("search", "删除成功")
  }

  def searchQuestion(): View = {
    val questionSeq = get("questionSeq")

    val entityQuery = OqlBuilder.from(classOf[Question], "question")
    entityQuery.where(
      "question.indicator.beginOn <= :now and (question.indicator.endOn is null or question.indicator.endOn >= :now)",
      LocalDate.now)
    if (!get("indicatorId").isEmpty) {
      val typeId = getLong("indicatorId").get
      if (typeId != 0L) {
        entityQuery.where("question.indicator.id=:id", typeId)
      }
    }
    if (questionSeq.isEmpty) {
      entityQuery.where("question.id not in (:questionIds)", questionSeq)
    }
    put("questionSeqIds", questionSeq)
    val questions = entityDao.search(entityQuery)
    put("questions", questions)
    forward()
  }

}
