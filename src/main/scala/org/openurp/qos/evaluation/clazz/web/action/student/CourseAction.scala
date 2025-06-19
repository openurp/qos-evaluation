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
import org.openurp.qos.evaluation.app.course.service.StdEvaluateSwitchService
import org.openurp.qos.evaluation.clazz.model.{EvaluateResult, QuestionResult, QuestionnaireClazz}
import org.openurp.qos.evaluation.base.model.{Option, Question}
import org.openurp.starter.web.support.StudentSupport

import java.time.{Instant, LocalDate}

class CourseAction extends StudentSupport {

  var evaluateSwitchService: StdEvaluateSwitchService = _

  protected override def projectIndex(student: Student): View = {
    put("currentSemester", getSemester)
    forward()
  }

  /**
   * 查询问卷
   *
   * @return
   */
  def search(): View = {
    val std = getStudent
    val semesterId = getInt("semester.id").getOrElse(0)
    val semester = entityDao.get(classOf[Semester], semesterId)
    val clazzList = getStdClazzs(std, semester)
    // 获得(课程问卷,根据学生,根据教学任务)
    var myClazzs: Seq[QuestionnaireClazz] = Seq()
    if (!clazzList.isEmpty) {
      val query = OqlBuilder.from(classOf[QuestionnaireClazz], "questionnaireClazz")
      query.where("questionnaireClazz.clazz in (:clazzList)", clazzList)
      val myquestionnaires = entityDao.search(query)
      myClazzs = myquestionnaires
    }
    // 获得(评教结果,根据学生)
    val evaluateMap = getClazzIdAndTeacherIdOfResult(std, semester)
    put("evaluateMap", evaluateMap)
    put("questionnaireClazzs", myClazzs)
    forward()
  }

  def getClazzIdAndTeacherIdOfResult(student: Student, semester: Semester): collection.Map[String, String] = {
    val query = OqlBuilder.from(classOf[EvaluateResult], "evaluateResult")
    query.where("evaluateResult.student = :student ", student)
    query.where("evaluateResult.clazz.semester = :semester", semester)
    val a = entityDao.search(query)
    a.map(obj => (obj.clazz.id.toString + "_" + (if (null == obj.teacher) "0" else obj.teacher.id.toString), "1")).toMap
  }

  def getStdClazzs(student: Student, semester: Semester): Seq[Clazz] = {
    val query = OqlBuilder.from[Clazz](classOf[CourseTaker].getName, "courseTaker")
    query.select("courseTaker.clazz")
    query.where("courseTaker.std=:std", student)
    query.where("courseTaker.semester =:semester", semester)
    entityDao.search(query)
  }

  /**
   * 跳转(问卷页面)
   */
  def loadQuestionnaire(): View = {
    val clazzId = get("clazzId").get
    val evaluateState = get("evaluateState").get
    val semesterId = getInt("semester.id").get
    val ids = get("clazzId").get.split(",")
    // 获得(教学任务)
    val clazz = entityDao.get(classOf[Clazz], ids(0).toLong)
    if (null == clazz) {
      addMessage("找不到该课程!")
      return forward("errors")
    }
    val evaluateSwitchs = evaluateSwitchService.getEvaluateSwitch(clazz.semester, clazz.project)
    if (evaluateSwitchs.isEmpty) {
      addMessage("现在还没有开放课程评教!")
      return forward("errors")
    }
    val evaluateSwitch = evaluateSwitchs.head
    if (!evaluateSwitch.isOpenedAt(Instant.now)) {
      addMessage("不在课程评教开放时间内,开放时间为：!" + evaluateSwitch.beginAt + "～" + evaluateSwitch.endAt)
      return forward("errors")
    }
    // 获得(课程问卷,根据教学任务)
    val questionnaireClazzs = entityDao.findBy(classOf[QuestionnaireClazz], "clazz.id", List(clazz.id))
    if (questionnaireClazzs.isEmpty) {
      addMessage("缺失评教问卷!")
      return forward("errors")
    }

    val questionnaireClazz = questionnaireClazzs.head
    val questions = questionnaireClazz.questionnaire.questions
    questions.sortWith((x, y) => x.priority < y.priority)

    // 获得(教师列表,根据学生教学任务)
    val teachers = Collections.newBuffer[Teacher]
    if (questionnaireClazz.evaluateByTeacher) {
      val teacher = entityDao.get(classOf[Teacher], ids(1).toLong)
      teachers += teacher
    } else {
      teachers ++= clazz.teachers
    }

    // 判断(是否更新)
    if ("update".equals(evaluateState)) {
      var teacherId: Long = 0
      if (questionnaireClazz.evaluateByTeacher) {
        teacherId = ids(1).toLong
      } else {
        teacherId = teachers.head.id
      }
      val std = getStudent
      val evaluateResult = getResultByStdIdAndClazzId(std.id, clazz.id, teacherId)
      if (null == evaluateResult) {
        addMessage("error.dataRealm.insufficient")
        forward("errors")
      }
      // 组装(问题结果)
      val questionMap = evaluateResult.questionResults.map(q => (q.question.id.toString, q.option.id)).toMap
      put("questionMap", questionMap)
      put("evaluateResult", evaluateResult)
    }

    put("clazz", clazz)
    put("teachers", teachers)
    put("questions", questions)
    //questionnaire = entityDao.get(classOf[Questionnaire], questionnaireClazz.questionnaire.id)
    put("questionnaire", questionnaireClazz.questionnaire)
    put("evaluateState", evaluateState)
    forward()
  }

  def getResultByStdIdAndClazzId(stdId: Long, clazzId: Long, teacherId: Long): EvaluateResult = {
    val query = OqlBuilder.from(classOf[EvaluateResult], "evaluateResult")
    query.where("evaluateResult.student.id =:stdId", stdId)
    query.where("evaluateResult.clazz.id =:clazzId", clazzId)
    if (0 != teacherId) {
      query.where("evaluateResult.teacher.id =:teacherId", teacherId)
    } else {
      query.where("evaluateResult.teacher is null")
    }
    val result = entityDao.search(query)

    if (result.size > 0) result.head else null.asInstanceOf[EvaluateResult]
  }

  /**
   * 保存问卷结果
   *
   * @return
   */
  def save(): View = {
    val std = getStudent
    val clazzId = getLong("clazz.id").get
    val teacherId = getLong("teacherId").get
    val teacherIds = Strings.splitToLong(get("teacher.ids", ""))
    // 根据教学任务,获得课程问卷
    val query = OqlBuilder.from(classOf[QuestionnaireClazz], "questionnaireClazz")
    query.where("questionnaireClazz.clazz.id =:clazzId", clazzId)
    val questionnaireClazzs = entityDao.search(query)
    if (questionnaireClazzs.isEmpty) {
      addMessage("field.evaluate.questionnaire")
      forward("errors")
    }
    val questionnaireClazz = questionnaireClazzs.head
    // 查询(评教结果)
    var evaluateResults: Seq[EvaluateResult] = Seq()
    val queryResult = OqlBuilder.from(classOf[EvaluateResult], "evaluateResult")
    queryResult.where("evaluateResult.clazz.id =:clazzId", clazzId)
    //    queryResult.where("evaluateResult.clazz.semester.id =:semesterId",semesterId)
    queryResult.where("evaluateResult.student =:std", std)
    // 如果教师为空
    if (teacherIds.size == 0) {
      evaluateResults = entityDao.search(queryResult)
    } else if (teacherIds.size == 1) {
      queryResult.where("evaluateResult.teacher.id =:teacherId", teacherId)
      evaluateResults = entityDao.search(queryResult)
    } //    如果是多个教师且为课程评教
    else if (teacherIds.size > 1) {
      queryResult.where("evaluateResult.teacher.id in(:teacherIds)", teacherIds)
      evaluateResults = entityDao.search(queryResult)
    }

    var clazz: Clazz = null
    var teacher: Teacher = null
    val newTeacherIds = Collections.newBuffer[Long]
    try {
      // 更新评教记录
      if (evaluateResults.nonEmpty) {
        evaluateResults foreach { evaluateResult =>
          clazz = evaluateResult.clazz
          teacher = evaluateResult.teacher
          newTeacherIds += teacher.id
          // 修改(问题选项)
          val questionResults = evaluateResult.questionResults
          val questions = questionnaireClazz.questionnaire.questions
          // 判断(是否添加问题)
          val oldQuestions = Collections.newBuffer[Question]
          questionResults foreach { questionResult =>
            oldQuestions += questionResult.question
          }
          questions foreach { question =>
            if (!oldQuestions.contains(question)) {
              val optionId = getLong("select" + question.id).get
              val option = entityDao.get(classOf[Option], optionId)
              val questionResult = new QuestionResult()
              questionResult.indicator = question.indicator
              questionResult.question = question
              questionResult.option = option
              questionResult.result = evaluateResult
              evaluateResult.questionResults += questionResult
            }
          }
          // 重新赋值
          //          evaluateResult.remark = get("evaluateResult.remark").getOrElse("")
          // 修改
          questionResults foreach { questionResult =>
            val question = questionResult.question
            val optionId = getLong("select" + question.id).get
            if (optionId == 0L) {
              questionResult.result = null
              entityDao.remove(questionResult)
            }
            if (!questionResult.option.id.equals(optionId)) {
              val option = entityDao.get(classOf[Option], optionId)
              questionResult.option = option
              questionResult.score = question.score * option.proportion.floatValue()
            }
          }
          entityDao.saveOrUpdate(questionResults)
        }
        var newId: Long = 0L
        //       一门课有新增教师，要为新增教师新增评教记录
        if (teacherIds.size > newTeacherIds.size) {
          teacherIds foreach { id =>
            if (!newTeacherIds.contains(id)) {
              newId = id
            }
          }
          val evaluateResult = new EvaluateResult()
          evaluateResult.clazz = clazz
          evaluateResult.department = clazz.teachDepart
          evaluateResult.student = std
          evaluateResult.statType = 1
          evaluateResult.teacher = entityDao.get(classOf[Teacher], newId)
          evaluateResult.updatedAt = Instant.now
          questionnaireClazz.questionnaire.questions foreach { question =>
            val optionId = getLong("select" + question.id).get
            val option = entityDao.get(classOf[Option], optionId)
            val questionResult = new QuestionResult()
            questionResult.question = question
            questionResult.indicator = question.indicator
            questionResult.result = evaluateResult
            questionResult.option = option
            questionResult.score = question.score * option.proportion.floatValue()
            evaluateResult.questionnaire = questionnaireClazz.questionnaire
            evaluateResult.questionResults += questionResult
          }
          //          evaluateResult.remark = get("evaluateResult.remark").getOrElse("")
          entityDao.saveOrUpdate(evaluateResult)
        }
      } //      新增评教记录
      else {
        clazz = entityDao.get(classOf[Clazz], clazzId)
        val teachers = entityDao.find(classOf[Teacher], teacherIds)

        // 获得(问卷)
        val questionnaire = questionnaireClazz.questionnaire
        if (questionnaire == null || questionnaire.questions == null) {
          addMessage("评教问卷有误!")
          forward("errors")
        }

        //  一个教师
        if (teachers.size == 1) {
          teacher = teachers.head
          var evaluateTeacher = teacher
          val evaluateResult = new EvaluateResult()
          evaluateResult.clazz = clazz
          evaluateResult.department = clazz.teachDepart
          evaluateResult.student = std
          evaluateResult.teacher = evaluateTeacher
          evaluateResult.statType = 1
          evaluateResult.updatedAt = Instant.now
          questionnaire.questions foreach { question =>
            val optionId = getLong("select" + question.id).get
            val option = entityDao.get(classOf[Option], optionId)
            val questionResult = new QuestionResult()
            questionResult.question = question
            questionResult.indicator = question.indicator
            questionResult.result = evaluateResult
            questionResult.option = option
            questionResult.score = question.score * option.proportion.floatValue()
            evaluateResult.questionnaire = questionnaire
            evaluateResult.questionResults += questionResult
          }
          //          evaluateResult.remark = get("evaluateResult.remark").getOrElse("")
          evaluateResult.score = evaluateResult.questionResults.foldLeft(0f)(_ + _.score)
          entityDao.saveOrUpdate(evaluateResult)
        }
        //        如果是按照课程评教，且是多个教师
        if (teachers.size > 1 & (!questionnaireClazz.evaluateByTeacher)) {
          teachers foreach { teacher =>
            val evaluateResult = new EvaluateResult()
            evaluateResult.clazz = clazz
            evaluateResult.statType = 1
            evaluateResult.department = clazz.teachDepart
            evaluateResult.student = std
            evaluateResult.teacher = teacher
            evaluateResult.updatedAt = Instant.now
            questionnaire.questions foreach { question =>
              val optionId = getLong("select" + question.id).get
              val option = entityDao.get(classOf[Option], optionId)
              val questionResult = new QuestionResult()
              questionResult.question = question
              questionResult.indicator = question.indicator
              questionResult.result = evaluateResult
              questionResult.option = option
              questionResult.score = question.score * option.proportion.floatValue()
              evaluateResult.questionnaire = questionnaire
              evaluateResult.questionResults += questionResult
            }
            evaluateResult.score = evaluateResult.questionResults.foldLeft(0f)(_ + _.score)
            //            evaluateResult.remark = get("evaluateResult.remark").getOrElse("")
            entityDao.saveOrUpdate(evaluateResult)
          }
        }
      }
      redirect("search", "&semester.id=" + clazz.semester.id, "info.save.success")
    } catch {
      case e: Exception =>
        e.printStackTrace()
        redirect("search", "&semester.id=" + clazz.semester.id, "info.save.failure")
    }
  }

}
