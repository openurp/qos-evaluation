package org.openurp.edu.evaluation.course.web.action

import org.beangle.commons.collection.Collections
import org.beangle.data.dao.OqlBuilder
import org.beangle.webmvc.api.action.ServletSupport
import org.beangle.webmvc.api.view.View
import org.beangle.webmvc.entity.action.RestfulAction
import org.openurp.base.model.Department
import org.openurp.base.model.Semester
import org.openurp.edu.base.code.model.Education
import org.openurp.edu.base.code.model.StdType
import org.openurp.edu.evaluation.lesson.result.model.EvaluateResult
import org.openurp.edu.evaluation.lesson.result.model.QuestionResult
import org.openurp.edu.evaluation.lesson.stat.model.LessonEvalStat
import org.openurp.edu.evaluation.lesson.stat.model.OptionStat
import org.openurp.edu.evaluation.lesson.stat.model.QuestionTypeStat
import org.openurp.edu.evaluation.lesson.stat.model.QuestionTypeStat
import org.openurp.edu.evaluation.lesson.stat.model.QuestionTypeStat
import org.openurp.edu.evaluation.lesson.stat.model.QuestionTypeStat
import org.openurp.edu.evaluation.model.EvaluationCriteria
import org.openurp.edu.evaluation.model.EvaluationCriteriaItem
import org.openurp.edu.evaluation.model.QuestionType
import org.openurp.edu.evaluation.model.Questionnaire
import org.openurp.edu.base.model.Teacher
import java.time.LocalDate

class QuestionnaireStatAction extends RestfulAction[LessonEvalStat] with ServletSupport {

  override def index(): View = {
    val stdType = entityDao.get(classOf[StdType], 5)
    put("stdTypeList", stdType)
    val department = entityDao.get(classOf[Department], 20)
    put("departmentList", department)

    var searchFormFlag = get("searchFormFlag").orNull
    if (searchFormFlag == null) {
      searchFormFlag = "beenStat"
    }
    put("searchFormFlag", searchFormFlag)
    //    put("educations", getEducations())
    put("departments", entityDao.getAll(classOf[Department]))
    val query = OqlBuilder.from(classOf[Questionnaire], "questionnaire").where("questionnaire.state =:state", true)
    put("questionnaires", entityDao.search(query))
    val semesters = entityDao.getAll(classOf[Semester])
    put("semesters", semesters)
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    put("currentSemester", entityDao.search(semesterQuery).head)
    put("evaluationCriterias", entityDao.getAll(classOf[EvaluationCriteria]))
    put("questionTypes", entityDao.getAll(classOf[QuestionType]))
    forward()
  }

  /**
   * 更新(教师)
   *
   * @return
   */
  def modifyTeacher(): View = {
    val quenStatId = longId("questionnaireStat")
    put("questionnaireStat", entityDao.get(classOf[LessonEvalStat], quenStatId));
    forward()
  }

  /**
   * 查询 (获得教师)
   *
   * @throws IOException
   */
  def searchTeacher() {
    // throws IOException {
    val code = get("teacherCode").get;
    val teachers = entityDao.search(OqlBuilder.from(classOf[Teacher], "sf").where("sf.code=:code", code))
    if (!teachers.isEmpty) {
      val teacher = teachers(0)
      response.setCharacterEncoding("utf-8");
      response.getWriter().print(
        teacher.id + "_" + teacher.user.name + "_" + teacher.user.department.name);
    } else {
      response.getWriter().print("");
    }
  }

  /**
   * 保存(更新教师)
   *
   * @return
   */
  def saveTeacher(): View = {
    val questionnaireStat = entityDao.get(classOf[LessonEvalStat], getLong("questionnaireStat.id").get);
    questionnaireStat.teacher = entityDao.get(classOf[Teacher], getLong("teacher.id").get)
    entityDao.saveOrUpdate(questionnaireStat);
    redirect("search", "info.update.success");
  }

  override def remove(): View = {
    val idStr = get("questionnaireStat.ids").get
    val Ids = idStr.split(",");
    val questionSIds = Array[Long]()
    for (i <- 0 to Ids.length) {
      questionSIds(i) = Ids(i).toLong
    }
    val query = OqlBuilder.from(classOf[LessonEvalStat], "questionS");
    query.where("questionS.id in(:ids)", questionSIds);
    val li = entityDao.search(query);
    try {
      li foreach { questionnaireStat =>

        if (questionnaireStat.questionStats.size > 0) {
          val questionStats = questionnaireStat.questionStats

          questionStats foreach { questionstat =>

            val options = entityDao.search(OqlBuilder.from(classOf[OptionStat], "op").where("op.questionStat.id=:id", questionstat.id))
            val optionStats = Collections.newBuffer[OptionStat]

            options foreach { optionStat =>

              questionstat.optionStats -= optionStat
              optionStats += optionStat
            }
            if (optionStats.size > 0) {
              entityDao.remove(optionStats);
            }
            questionnaireStat.questionStats -= questionstat
            entityDao.remove(questionstat);

          }

        }
        if (questionnaireStat.questionTypeStats.size > 0) {
          val questionTS = questionnaireStat.questionTypeStats
          questionnaireStat.questionTypeStats --= questionTS
          entityDao.remove(questionTS);
        }
        entityDao.remove(questionnaireStat);

      }
    } catch {
      case e: Exception =>
        // TODO: handle exception
        redirect("search", "删除失败！");
    }
    redirect("search", "info.delete.success");
  }

  /**
   * 跳转(统计首页面)
   */
  def statHome(): View = {
    put("stdTypeList", entityDao.getAll(classOf[StdType]));
    put("departmentList", entityDao.search(OqlBuilder.from(classOf[Department], "de").where("de.teaching=:tea", true)));
    put("semester", 20141)
    put("educations", entityDao.getAll(classOf[Education]))
    put("departments", entityDao.search(OqlBuilder.from(classOf[Department], "de").where("de.teaching=:tea", true)));
    forward()
  }

  /**
   * 跳转(初始有效值页面)
   */
  def initValidHome(): View = {
    put("stdTypeList", entityDao.getAll(classOf[StdType]));
    put("departmentList", entityDao.search(OqlBuilder.from(classOf[Department], "de").where("de.teaching=:tea", true)));
    put("semester", 20141)
    forward()
  }

  /**
   * 跳转(院系评教比较,根据所属部门)
   *
   * @return
   */
  def departDistributeStat(): View = {
    val semesterQuery = OqlBuilder.from(classOf[Semester], "semester").where(":now between semester.beginOn and semester.endOn", LocalDate.now)
    val semesterId = getInt("semester.id").getOrElse(entityDao.search(semesterQuery).head.id)
    val lis = entityDao.search(OqlBuilder.from(classOf[EvaluationCriteriaItem], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L))
    if (lis.size < 1) { redirect("search", "未找到评价标准！"); }
    put("criterias", lis);
    put("departments", entityDao.search(OqlBuilder.from(classOf[Department], "dep").where("dep.teaching=:tea", true)))
    put("semester", entityDao.get(classOf[Semester], semesterId));
    val que = OqlBuilder.from[Double](classOf[EvaluateResult].getName + " evaluateResult," + classOf[QuestionResult].getName + " questionResult");
    que.select("sum(questionResult.score)/count(distinct evaluateResult.id)");
    que.where("evaluateResult.id=questionResult.result.id");
    que.where("evaluateResult.lesson.semester.id=" + semesterId);
    val lit = entityDao.search(que);
    var fl = 0d;
    if (lit.size > 0) {
      if (lit(0) != 0f) {
        fl = lit(0)
      }
    }
    put("evaluateResults", fl);
    val hql = OqlBuilder.from(classOf[LessonEvalStat], "evaluateR")
    hql.select("evaluateR.lesson.teachDepart.id,count( evaluateR.teacher.id)")
    hql.where("evaluateR.lesson.semester.id=" + semesterId)
    hql.groupBy("evaluateR.lesson.teachDepart.id,evaluateR.lesson.semester.id")
    put("questionNums", entityDao.search(hql));
    val maps = Collections.newMap[String, Seq[Array[Any]]]
    lis foreach { evaluationCriteriaItem =>
      val query = OqlBuilder.from[Array[Any]](classOf[LessonEvalStat].getName, "questionnaireStat");
      query.where("questionnaireStat.semester.id=:semesterId", semesterId);
      query.select("questionnaireStat.lesson.teachDepart.id,count(questionnaireStat.teacher.id)");
      query.where("questionnaireStat.score>=" + evaluationCriteriaItem.min
        + " and questionnaireStat.score<" + evaluationCriteriaItem.max);
      query.groupBy("questionnaireStat.lesson.teachDepart.id");
      maps.put(evaluationCriteriaItem.id.toString(), entityDao.search(query));

    }
    put("questionDeps", maps);
    forward()
  }

}
