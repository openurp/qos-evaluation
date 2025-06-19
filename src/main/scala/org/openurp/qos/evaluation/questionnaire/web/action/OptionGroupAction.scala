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
import org.openurp.base.model.Project
import org.openurp.qos.evaluation.base.model.{Option, OptionGroup, Question}
import org.openurp.starter.web.support.ProjectSupport

class OptionGroupAction extends RestfulAction[OptionGroup] with ProjectSupport {

  override def search(): View = {
    val builder = OqlBuilder.from(classOf[OptionGroup], "optionGroup")
    populateConditions(builder)
    builder.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    val optionGroups = entityDao.search(builder)

    put("optionGroups", optionGroups)
    forward()
  }

  override def editSetting(entity: OptionGroup): Unit = {
    var c = 5 - entity.options.size
    if (c > 0) {
      for (i <- 0 until c) {
        entity.options += new Option()
      }
    }
    super.editSetting(entity)
  }

  override def saveAndRedirect(entity: OptionGroup): View = {
    try {
      val optionGroup = entity.asInstanceOf[OptionGroup]
      optionGroup.options.clear()
      val optionCount = getInt("optionCount", 0)
      (0 until optionCount) foreach { i =>
        get("option" + i + ".name") foreach { optionName =>
          val option = populateEntity(classOf[Option], "option" + i)
          if (!(option.name == "--")) {
            option.group = optionGroup
            optionGroup.options += option
          }
        }
      }
      if (null == optionGroup.project) {
        optionGroup.project = getProject
      }
      optionGroup.name = optionGroup.name.replaceAll("<", "&#60;").replaceAll(">", "&#62;")
      entityDao.saveOrUpdate(optionGroup)
      return redirect("search", "info.save.success")
    } catch {
      case e: Exception =>
        logger.info("saveAndForwad failure", e)
        return redirect("search", "info.save.failure")
    }

  }

  protected override def getQueryBuilder: OqlBuilder[OptionGroup] = {
    val builder = OqlBuilder.from(classOf[OptionGroup], simpleEntityName)
    populateConditions(builder)
    builder.where("optionGroup.depart.id in (:departIds)")
    builder.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
  }

  override def remove(): View = {
    val optionGroupIds = getLongIds("optionGroup")
    val query1 = OqlBuilder.from(classOf[OptionGroup], "optionGroup")
    query1.where("optionGroup.id in (:optionGroupIds)", optionGroupIds)
    val optionGroups = entityDao.search(query1)

    val query = OqlBuilder.from(classOf[Question], "question")
    query.where("question.optionGroup.id in (:optionGroups)", optionGroupIds)
    val questions = entityDao.search(query)
    if (!questions.isEmpty) return redirect("search", "删除失败,选择的数据中已有被评教问题引用")
    else entityDao.remove(optionGroups)
    return redirect("search", "info.remove.success")
  }

}
