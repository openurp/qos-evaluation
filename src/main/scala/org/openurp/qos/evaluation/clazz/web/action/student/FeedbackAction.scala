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

package org.openurp.qos.evaluation.clazz.web.action.student

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.data.dao.OqlBuilder
import org.beangle.security.Securities
import org.beangle.webmvc.view.View
import org.beangle.webmvc.support.action.RestfulAction
import org.openurp.base.hr.model.Teacher
import org.openurp.base.model.Semester
import org.openurp.base.std.model.Student
import org.openurp.edu.clazz.model.{Clazz, CourseTaker}
import org.openurp.qos.evaluation.app.course.model.TextEvaluateSwitch
import org.openurp.qos.evaluation.clazz.model.Feedback
import org.openurp.starter.web.support.{ProjectSupport, StudentSupport}

import java.time.{Instant, LocalDate}
import scala.collection.mutable.Buffer

class FeedbackAction extends StudentSupport {

  protected override def projectIndex(student: Student): View = {
    put("currentSemester", getSemester)
    forward()
  }

  def search(): View = {
    val std = getStudent
    // 页面条件
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val clazzs = getStdClazzs(std, semester)
    // 获得(我的课程)

    if (clazzs.isEmpty) {
      addMessage("对不起,没有评教课程!")
      forward("errors")
    }
    val myCourses = Collections.newBuffer[Clazz]
    clazzs foreach { clazz =>
      if (clazz.teachers.nonEmpty) {
        myCourses += clazz
      }
    }
    put("clazzs", myCourses)
    // 获得(文字评教-已经评教)
    put("evaluateMap", getClazzIdAndTeacherIdOfResult(std, semester))
    forward()
  }

  def getClazzIdAndTeacherIdOfResult(student: Student, semester: Semester): collection.Map[String, String] = {
    val query = OqlBuilder.from(classOf[Feedback], "fb")
    query.where("fb.std = :student ", student)
    query.where("fb.semester = :semester", semester)
    entityDao.search(query).map(x => "${x.course.id}_${x.teacher.id}" -> "1").toMap
  }

  def getStdClazzs(student: Student, semester: Semester): Seq[Clazz] = {
    val query = OqlBuilder.from(classOf[CourseTaker], "taker")
    query.select("distinct taker.clazz.id ")
    query.where("taker.std=:std", student)
    query.where("taker.semester =:semester", semester)
    val clazzIds = entityDao.search(query)
    var stdClazzs: Seq[Clazz] = Seq()
    if (clazzIds.nonEmpty) {
      val entityquery = OqlBuilder.from(classOf[Clazz], "clazz").where("clazz.id in (:clazzIds)", clazzIds)
      stdClazzs = entityDao.search(entityquery)
    }
    stdClazzs
  }

  def loadTextEvaluate(): View = {
    val evaluateId = get("evaluateId").get
    val evaluateState = get("evaluateState").get
    val ids = get("evaluateId").get.split(",")
    // 获得(教学任务)
    val clazz = entityDao.get(classOf[Clazz], ids(0).toLong)
    if (null == clazz) {
      addMessage("找不到该课程!")
      return forward("errors")
    }
    val textEvaluationSwitch = getSwitch()
    if (null == textEvaluationSwitch) {
      addMessage("现在还没有开放文字评教!")
      forward("errors")
    }
    // 获得(教师)
    val teacher = entityDao.get(classOf[Teacher], ids(1).toLong)
    if (null == teacher) {
      addMessage("该课程没有指定任课教师!")
      forward("errors")
    }
    // 判断(是否更新)
    if ("update".equals(evaluateState)) {
      val std = getStudent
      val textEvaluations = getFeedbackList(std, clazz, teacher)
      put("textEvaluations", textEvaluations)
    }
    put("teacher", teacher)
    put("clazz", clazz)
    put("evaluateState", evaluateState)
    forward()
  }

  def getFeedbackList(student: Student, clazz: Clazz, teacher: Teacher): Seq[Feedback] = {
    val query = OqlBuilder.from(classOf[Feedback], "textEvaluation")
    query.where("textEvaluation.student =:student", student)
    query.where("textEvaluation.clazz =:clazz", clazz)
    query.where("textEvaluation.teacher =:teacher", teacher)
    entityDao.search(query)
  }

  def getSwitch(): TextEvaluateSwitch = {
    val iterator: Iterator[TextEvaluateSwitch] = entityDao.getAll(classOf[TextEvaluateSwitch]).iterator
    if (iterator.hasNext)
      iterator.next()
    else new TextEvaluateSwitch()
  }

  def saveTextEvaluate(): View = {
    val std = getStudent
    val clazzId = getLong("clazz.id")
    val teacherId = getLong("teacherId")
    val clazz = entityDao.get(classOf[Clazz], clazzId.get)
    val teacher = entityDao.get(classOf[Teacher], teacherId.get)
    val textOpinion = get("textOpinion").get
    try {
      if (!textOpinion.isEmpty) {
        val feedback = new Feedback()
        feedback.std = std
        feedback.semester = clazz.semester
        feedback.crn = clazz.crn
        feedback.course = clazz.course
        feedback.teacher = teacher
        feedback.teachDepart = clazz.teachDepart
        feedback.contents = textOpinion
        feedback.updatedAt = Instant.now
        feedback.grade = "--" //FIXME
        entityDao.saveOrUpdate(feedback)
      }
      redirect("search", "&semester.id=" + clazz.semester.id, "info.save.success")
    } catch {
      case e: Exception =>
        e.printStackTrace()
        redirect("search", "&semester.id=" + clazz.semester.id, "info.save.failure")
    }
  }

  def remsgList(): View = {
    val std = getStudent
    val ids = Strings.splitToLong(get("clazz.ids", ""))
    val teachers = getTeachersByClazzIdSeq(ids)
    val clazzs = getTeacherClazzByClazzIdSeq(ids)
    val clazz = entityDao.get(classOf[Clazz], ids.head)
    val semester = clazz.semester

    put("clazzs", clazzs)
    put("textEvaluationMap", getMyFeedbackMap(std, semester, teachers))
    forward()
  }

  def getMyFeedbackMap(std: Student, semester: Semester, teachers: Seq[Teacher]): collection.Map[Long, Buffer[Feedback]] = {
    val query = OqlBuilder.from(classOf[Feedback], "textEvaluation")
    query.where("textEvaluation.student =:std", std)
    query.where("textEvaluation.clazz.semester =:semester", semester)
    query.where("textEvaluation.audited = true")
    val textEvaluateMap = Collections.newMap[Long, Buffer[Feedback]]
    val results = entityDao.search(query)
    results foreach { textEvaluation =>
      textEvaluateMap.getOrElseUpdate(textEvaluation.teacher.id, Collections.newBuffer[Feedback]) += textEvaluation
    }
    for (teacher <- teachers) {
      if (!textEvaluateMap.contains(teacher.id)) {
        textEvaluateMap.put(teacher.id, null)
      }
    }
    textEvaluateMap
  }

  def getTeachersByClazzIdSeq(clazzIdSeq: Iterable[Long]): Seq[Teacher] = {
    val query = OqlBuilder.from[Teacher](classOf[Clazz].getName + " clazz")
    query.join("clazz.teachers", "teacher")
    query.select("teacher")
    query.where("clazz.id in (:clazzIdSeq)", clazzIdSeq)
    entityDao.search(query)
  }

  def getTeacherClazzByClazzIdSeq(clazzIdSeq: Iterable[Long]): Seq[Array[Any]] = {
    val query = OqlBuilder.from[Array[Any]](classOf[Clazz].getName + " clazz")
    query.join("clazz.teachers", "teacher")
    query.select("teacher,clazz")
    query.where("clazz.id in (:clazzIdSeq)", clazzIdSeq)
    entityDao.search(query)
  }


}
