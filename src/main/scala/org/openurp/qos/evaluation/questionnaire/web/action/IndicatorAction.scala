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
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.qos.evaluation.base.model.{Indicator, Question}
import org.openurp.starter.web.support.ProjectSupport

import java.time.{Instant, LocalDate}

class IndicatorAction extends RestfulAction[Indicator] with ProjectSupport {

  override def search(): View = {
    val builder = OqlBuilder.from(classOf[Indicator], "indicator")
    populateConditions(builder)
    builder.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    val indicators = entityDao.search(builder)
    put("indicators", indicators)
    forward()
  }

  protected override def saveAndRedirect(entity: Indicator): View = {
    try {
      val indicator = entity.asInstanceOf[Indicator]
      indicator.project = getProject
      indicator.updatedAt = Instant.now

      val name = indicator.name
      val enName = indicator.enName.orNull
      val remark = indicator.remark.orNull
      indicator.beginOn = LocalDate.parse(get("indicator.beginOn").get)
      indicator.endOn =
        get("indicator.endOn") match {
          case Some(endOn) =>
            if (endOn.isEmpty) None else Some(LocalDate.parse(endOn))
          case None => None
        }
      if (remark != null) {
        indicator.remark = Some(remark.replaceAll("<", "&#60;").replaceAll(">", "&#62;"))
      }
      if (enName != null) {
        indicator.enName = Some(enName.replaceAll("<", "&#60;").replaceAll(">", "&#62;"))
      }
      indicator.name = name.replaceAll("<", "&#60;").replaceAll(">", "&#62;")
      indicator.updatedAt = Instant.now
      entityDao.saveOrUpdate(indicator)
      redirect("search", "info.save.success")
    } catch {
      case e: Exception =>
        logger.info("saveAndForwad failure", e)
        redirect("search", "info.save.failure")
    }
  }

  override def remove(): View = {
    val indicatorIds = getLongIds("indicator")
    val query1 = OqlBuilder.from(classOf[Indicator], "indicator")
    query1.where("indicator.id in (:indicatorIds)", indicatorIds)
    val indicators = entityDao.search(query1)

    val query = OqlBuilder.from(classOf[Question], "question")
    query.where("question.indicator in (:indicators)", indicators)
    val questions = entityDao.search(query)
    if (questions.nonEmpty) {
      return redirect("search", "删除失败,选择的数据中已有被评教问题引用");
    }

    entityDao.remove(indicators)
    redirect("search", "info.remove.success")
  }

}
