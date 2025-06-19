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
import org.beangle.webmvc.support.action.{ImportSupport, RestfulAction}
import org.beangle.webmvc.view.{Stream, View}
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.{Department, Project, Semester}
import org.openurp.edu.clazz.model.Clazz
import org.openurp.qos.evaluation.app.department.model.EvaluateSwitch
import org.openurp.qos.evaluation.base.model.{Indicator, Question, Questionnaire}
import org.openurp.qos.evaluation.department.helper.ImportSupervisiorListener
import org.openurp.qos.evaluation.department.model.{SupervisiorEvaluate, SupervisiorQuestion}
import org.openurp.starter.web.support.ProjectSupport

import java.time.Instant
import scala.collection.mutable.Buffer

/**
 * @author xinzhou
 */
class SupervisiorEvaluateAction extends RestfulAction[SupervisiorEvaluate], ImportSupport[SupervisiorEvaluate], ProjectSupport {

  override def indexSetting(): Unit = {
    given project: Project = getProject

    put("departments", findInSchool(classOf[Department]))
    put("semesters", entityDao.getAll(classOf[Semester]))
    put("currentSemester", getSemester)
  }

  def importTeachers(): View = {
    val builder = OqlBuilder.from[Array[Any]](classOf[Clazz].getName, "clazz")
    getInt("supervisiorEvaluate.semester.id") foreach { semesterId => builder.where("clazz.semester.id=:id", semesterId) }
    builder.join("clazz.teachers", "teacher")
    builder.select("distinct teacher.id , clazz.teachDepart.id , clazz.semester.id")
    builder.where("not exists (from " + classOf[SupervisiorEvaluate].getName + " se where se.semester = clazz.semester and se.teacher = teacher and se.department = clazz.teachDepart)")
    val datas = entityDao.search(builder)
    val supervisiorEvaluates = Collections.newBuffer[SupervisiorEvaluate]
    datas foreach { data =>
      val supervisiorEvaluate = new SupervisiorEvaluate
      supervisiorEvaluate.teacher = new Teacher
      supervisiorEvaluate.teacher.id = data(0).asInstanceOf[Long]
      supervisiorEvaluate.department = new Department
      supervisiorEvaluate.department.id = data(1).asInstanceOf[Int]
      supervisiorEvaluate.semester = new Semester
      supervisiorEvaluate.semester.id = data(2).asInstanceOf[Int]
      supervisiorEvaluate.evaluateAt = Instant.now
      supervisiorEvaluate.questionnaire = entityDao.get(classOf[Questionnaire], 322L)
      supervisiorEvaluates += supervisiorEvaluate
    }
    entityDao.saveOrUpdate(supervisiorEvaluates)
    val semesterId = get("supervisiorEvaluate.semester.id").orNull
    redirect("search", s"orderBy=supervisiorEvaluate.teacher.staff.code asc&supervisiorEvaluate.semester.id=$semesterId", "导入完成")
  }

  override def editSetting(supervisiorEvaluate: SupervisiorEvaluate): Unit = {
    val semesterId = getIntId("supervisiorEvaluate.semester")
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
    supervisiorEvaluate.questionResults foreach { qr =>
      resultMap.put(qr.question, qr.score)
    }
    put("resultMap", resultMap)
    super.editSetting(supervisiorEvaluate)
  }

  override def saveAndRedirect(supervisiorEvaluate: SupervisiorEvaluate): View = {
    val questionnaire = entityDao.get(classOf[Questionnaire], 322L)
    val resultMap = Collections.newMap[Question, SupervisiorQuestion]
    supervisiorEvaluate.questionResults foreach { qr =>
      resultMap.put(qr.question, qr)
    }
    questionnaire.questions foreach { question =>
      resultMap.get(question) match {
        case Some(qr) => qr.score = getFloat(s"${question.id}_score").get
        case None =>
          val qr = new SupervisiorQuestion
          qr.question = question
          qr.result = supervisiorEvaluate
          qr.score = getFloat(s"${question.id}_score").get
          supervisiorEvaluate.questionResults += qr
      }
    }
    supervisiorEvaluate.calTotalScore()
    super.saveAndRedirect(supervisiorEvaluate)
  }

  def importTemplate(): View = {
    Stream(ClassLoaders.getResourceAsStream("supervisiorEvaluate.xls").get, MediaTypes.ApplicationXlsx, "评教结果.xlsx")
  }

  override protected def getQueryBuilder: OqlBuilder[SupervisiorEvaluate] = {
    val query = OqlBuilder.from(classOf[SupervisiorEvaluate], "supervisiorEvaluate")
    getBoolean("passed") match {
      case Some(true) => query.where("supervisiorEvaluate.totalScore is not null")
      case Some(false) => query.where("supervisiorEvaluate.totalScore is null")
      case None =>
    }
    populateConditions(query)
    query.orderBy(get(Order.OrderStr).orNull).limit(getPageLimit)
  }

  protected override def configImport(setting: ImportSetting): Unit = {
    setting.listeners = List(new ForeignerListener(entityDao), new ImportSupervisiorListener(entityDao))
  }
}
