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
import org.openurp.base.model.Department
import org.openurp.qos.evaluation.base.model.{AssessCriteria, AssessGrade}
import org.openurp.starter.web.support.ProjectSupport

class AssessCriteriaAction extends RestfulAction[AssessCriteria] with ProjectSupport {

  override def search(): View = {
    val builder = OqlBuilder.from(classOf[AssessCriteria], "criteria")
    populateConditions(builder)
    builder.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    val assessCriterias = entityDao.search(builder)
    put("assessCriterias", assessCriterias)
    forward()
  }

  override def editSetting(entity: AssessCriteria): Unit = {
    super.editSetting(entity)
  }

  protected override def saveAndRedirect(entity: AssessCriteria): View = {
    try {
      val assessCriteria = entity.asInstanceOf[AssessCriteria]
      assessCriteria.grades.clear()
      assessCriteria.project = getProject
      val criteriaCount = getInt("criteriaItemCount", 0)
      (0 until criteriaCount) foreach { i =>
        get("criteriaItem" + i + ".name") foreach { criteriaItemName =>
          val item = populateEntity(classOf[AssessGrade], "criteriaItem" + i)
          item.criteria = assessCriteria
          if (item.description == null) {
            item.description = item.name
          }
          assessCriteria.grades += item
        }
      }
      assessCriteria.name = (assessCriteria.name.replaceAll("<", "&#60;").replaceAll(">", "&#62;"))
      entityDao.saveOrUpdate(assessCriteria)
      return redirect("search", "info.save.success")
    } catch {
      case e: Exception =>
        logger.info("saveAndForwad failure", e)
        return redirect("search", "info.save.failure")
    }
  }

  /**
   * 不能删除默认对照标准
   */
  override def remove(): View = {
    val assessCriteriaIds = getLongIds("assessCriteria")
    if (assessCriteriaIds.contains(1L)) {
      return redirect("search", "info.delete.failure")
    } else {
      return super.remove()
    }
  }

  protected override def getQueryBuilder: OqlBuilder[AssessCriteria] = {
    val builder = OqlBuilder.from(classOf[AssessCriteria], simpleEntityName)
    populateConditions(builder)
    builder.where("assessCriteria.depart.id in (:departIds)", get("assessCriteria.depart.id"))
    builder.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
  }

}
