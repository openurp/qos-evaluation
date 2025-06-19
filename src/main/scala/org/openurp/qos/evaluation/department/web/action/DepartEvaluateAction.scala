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

package org.openurp.qos.evaluation.department.web.action

import org.beangle.commons.activation.MediaTypes
import org.beangle.commons.collection.{Collections, Order}
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.dao.OqlBuilder
import org.beangle.doc.transfer.importer.ImportSetting
import org.beangle.doc.transfer.importer.listener.ForeignerListener
import org.beangle.security.Securities
import org.beangle.webmvc.support.action.{ImportSupport, RestfulAction}
import org.beangle.webmvc.view.{Stream, View}
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Department, Project, Semester}
import org.openurp.edu.clazz.model.Clazz
import org.openurp.qos.evaluation.app.department.model.EvaluateSwitch
import org.openurp.qos.evaluation.base.model.{Indicator, Question, Questionnaire}
import org.openurp.qos.evaluation.department.helper.ImportDepartListener
import org.openurp.qos.evaluation.department.model.{DepartEvaluate, DepartQuestion}
import org.openurp.starter.web.support.ProjectSupport

import java.time.{Instant, LocalDate}
import scala.collection.mutable.Buffer

/**
 * @author xinzhou
 */
class DepartEvaluateAction extends RestfulAction[DepartEvaluate], ImportSupport[DepartEvaluate], ProjectSupport {

  override def indexSetting(): Unit = {
    given project: Project = getProject

    put("departments", findInSchool(classOf[Department]))
    put("semesters", entityDao.getAll(classOf[Semester]))
    put("currentSemester", getSemester)
  }

  def importTeachers(): View = {
    val builder = OqlBuilder.from[Array[Any]](classOf[Clazz].getName, "clazz")
    getInt("departEvaluation.semester.id") foreach { semesterId => builder.where("clazz.semester.id=:id", semesterId) }
    builder.join("clazz.teachers", "teacher")
    builder.select("distinct teacher.id , clazz.teachDepart.id , clazz.semester.id")
    //    builder.where("clazz.teachDepart.id=:departId", getteacher.department.id)
    builder.where("not exists (from " + classOf[DepartEvaluate].getName + " de where de.semester = clazz.semester and de.teacher = teacher and de.department = clazz.teachDepart)")
    val datas = entityDao.search(builder)
    val departEvaluates = Collections.newBuffer[DepartEvaluate]
    datas foreach { data =>
      val departEvaluate = new DepartEvaluate
      departEvaluate.teacher = new Teacher
      departEvaluate.teacher.id = data(0).asInstanceOf[Long]
      departEvaluate.department = new Department
      departEvaluate.department.id = data(1).asInstanceOf[Int]
      departEvaluate.semester = new Semester
      departEvaluate.semester.id = data(2).asInstanceOf[Int]
      departEvaluate.evaluateAt = Instant.now
      departEvaluate.questionnaire = entityDao.get(classOf[Questionnaire], 322L)
      departEvaluates += departEvaluate
    }
    entityDao.saveOrUpdate(departEvaluates)
    val semesterId = get("departEvaluate.semester.id").orNull
    redirect("search", s"orderBy=departEvaluate.teacher.staff.code asc&departEvaluate.semester.id=$semesterId", "导入完成")
  }

  override def editSetting(departEvaluate: DepartEvaluate): Unit = {
    val semesterId = getIntId("departEvaluate.semester")
    put("semester", entityDao.get(classOf[Semester], semesterId))

    val esbuilder = OqlBuilder.from(classOf[EvaluateSwitch], "es")
    esbuilder.where("es.questionnaire.id =:quId", 322L)
    esbuilder.where("es.semester.id = :semesterId", semesterId)
    esbuilder.where("es.opened = :opened", true)
    val evaluateSwitches = entityDao.search(esbuilder)
    put("evaluateSwitches", evaluateSwitches)

    if (evaluateSwitches.nonEmpty) {
      val questionnaire = evaluateSwitches.head.questionnaire
      put("questionnaire", questionnaire)

      val questionTree = Collections.newMap[Indicator, Buffer[Question]]
      questionnaire.questions foreach { question =>
        val key = question.indicator
        var questions: Buffer[Question] = questionTree.get(key).orNull
        if (null == questions) {
          questions = Collections.newBuffer
        }
        questions += question
        questions.sortWith((x, y) => x.priority < y.priority)
        questionTree.put(key, questions)
      }
      put("questionTree", questionTree)
    }
    val resultMap = Collections.newMap[Question, Float]
    departEvaluate.questionResults foreach { qr =>
      resultMap.put(qr.question, qr.score)
    }
    put("resultMap", resultMap)
    super.editSetting(departEvaluate)
  }

  override def saveAndRedirect(departEvaluate: DepartEvaluate): View = {
    val questionnaire = entityDao.get(classOf[Questionnaire], 322L)
    val resultMap = Collections.newMap[Question, DepartQuestion]
    departEvaluate.questionResults foreach { qr =>
      resultMap.put(qr.question, qr)
    }
    questionnaire.questions foreach { question =>
      resultMap.get(question) match {
        case Some(qr) => qr.score = getFloat(s"${question.id}_score").get
        case None =>
          val qr = new DepartQuestion
          qr.question = question
          qr.result = departEvaluate
          qr.score = getFloat(s"${question.id}_score").get
          departEvaluate.questionResults += qr
      }
    }
    departEvaluate.calTotalScore()
    super.saveAndRedirect(departEvaluate)
  }

  def importTemplate(): View = {
    Stream(ClassLoaders.getResourceAsStream("departEvaluate.xls").get, MediaTypes.ApplicationXlsx, "评教结果.xls")
  }

  override protected def getQueryBuilder: OqlBuilder[DepartEvaluate] = {
    val query = OqlBuilder.from(classOf[DepartEvaluate], "departEvaluate")
    getBoolean("passed") match {
      case Some(true) => query.where("departEvaluate.totalScore is not null")
      case Some(false) => query.where("departEvaluate.totalScore is null")
      case None =>
    }

    val builder = OqlBuilder.from(classOf[Teacher], "s")
      .where("s.user.code=:code", Securities.user)
      .where("s.project=:project", getProject)
    val teachers = entityDao.search(builder)
    if (teachers.isEmpty) {
      throw new RuntimeException("Cannot find teachers with code " + Securities.user)
    } else {

      query.where("departEvaluate.department.id=:id", teachers.head.department.id)
      populateConditions(query)
      query.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
    }
  }

  protected override def configImport(setting: ImportSetting): Unit = {
    setting.listeners = List(new ForeignerListener(entityDao), new ImportDepartListener(entityDao))
  }

}
