package org.openurp.edu.evaluation.teacher.web.action

import org.beangle.commons.collection.Collections
import org.beangle.commons.dao.OqlBuilder
import org.beangle.webmvc.entity.action.RestfulAction
import org.openurp.base.model.Department
import org.openurp.base.model.Semester
import org.openurp.base.model.Semester
import org.openurp.base.model.Semester
import org.openurp.edu.base.code.model.StdType
import org.openurp.edu.base.model.Teacher
import org.openurp.edu.evaluation.lesson.model.QuestionnaireLesson
import org.openurp.edu.evaluation.lesson.result.model.EvaluateResult
import org.openurp.edu.evaluation.lesson.result.model.QuestionResult
import org.openurp.edu.evaluation.lesson.stat.model.LessonEvalStat
import org.openurp.edu.evaluation.model.EvaluationCriteriaItem
import org.openurp.edu.evaluation.model.Option
import org.openurp.edu.evaluation.model.QuestionType
import org.openurp.edu.lesson.model.Lesson
class QuestionnaireStatTeacherAction extends RestfulAction[LessonEvalStat] {

  //  protected QuestionnairStatService questionnairStatService;

  override def index(): String = {
    put("stdTypeList", entityDao.getAll(classOf[StdType]));
    put("departmentList", entityDao.search(OqlBuilder.from(classOf[Department], "de").where("de.teaching=:tea", true)));
    //    getSemester();
    //    put("semesters", entityDao.getAll(classOf[Semester]));
    //    val teacher = getLoginTeacher();
    val teacher = entityDao.get(classOf[Teacher], 8589L)
    put("teacher", teacher);
    // OqlBuilder query = OqlBuilder.from(QuestionnaireStat.class,
    // "questionnaireStat");
    // query.select("distinct questionnaireStat.stdType");
    // query.where("questionnaireStat.teacher.id=:teacherId",
    // teacher.getId()));
    // List stdTypeList = (List) entityDao.search(query);
    // put("stdTypeList", stdTypeList);
    forward();
  }

  override def search(): String = {
    val entityQuery = OqlBuilder.from(classOf[LessonEvalStat], "questionnaireStat");
    populateConditions(entityQuery);
    //    val teacher = getLoginTeacher();
    val teacher = entityDao.get(classOf[Teacher], 8589L)
    entityQuery.join("questionnaireStat.lesson.teachers", "teacher");
    entityQuery.where("teacher.id=:teacherId", teacher.id);
    entityQuery.limit(getPageLimit);
    val orderBy = get("orderBy");
    if (orderBy != null && !"".equals(orderBy)) {
      entityQuery.orderBy("questionnaireStat.lesson.semester.schoolYear desc");
    }
    val questionnaireStatTeachers = entityDao.search(entityQuery);
    put("questionnaireStatTeachers", questionnaireStatTeachers);
    val questionTypeList = entityDao.search(OqlBuilder.from(classOf[QuestionType], "qt").where("qt.state=:state", true));
    //    Collections.sort(questionTypeList);
    put("questionTypeList", questionTypeList);
    put("criteria", entityDao.search(OqlBuilder.from(classOf[EvaluationCriteriaItem], "criteriaItem").where("criteriaItem.criteria.id =:id", 1L)))
    forward();
  }

  /**
   * 教师个人查询自己被评教的详细情况
   *
   * @param
   * @param form
   * @param request
   * @param response
   * @return @
   */
  def evaluatePersonInfo(): String = {
    val questionnaireStat = entityDao.get(classOf[LessonEvalStat], getLong("teacherStatId").get);
    put("questionnaireStat", questionnaireStat);
    val query = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName, "result");
    query.where("result.teacher=:teaId", questionnaireStat.teacher);
    query.where("result.lesson=:less", questionnaireStat.lesson);
    query.select("case when result.statType =1 then count(result.id) end,count(result.id)");
    query.groupBy("result.statType");
    entityDao.search(query) foreach { a =>
      put("number1", a(0));
      put("number2", a(1));
    }
    val list = Collections.newBuffer[Option]
    val questions = questionnaireStat.questionnaire.questions
    questions foreach { question =>

      val options = question.optionGroup.options
      options foreach { option =>

        var tt = 0;
        list foreach { oldOption =>

          if (oldOption.id.equals(option.id)) {
            tt += 1;
          }

        }
        if (tt == 0) {
          list += option
        }

      }

    }
    put("options", list);
    val querys = OqlBuilder.from[Integer](classOf[QuestionnaireLesson].getName, "questionnaireL");
    querys.join("questionnaireL.lesson.teachers", "teacher");
    querys.where("teacher=:teach", questionnaireStat.teacher);
    querys.where("questionnaireL.lesson=:lesso", questionnaireStat.lesson);
    querys.join("questionnaireL.lesson.teachclass.courseTakes", "courseTake");
    querys.select("count(courseTake.std.id)");
    put("numbers", entityDao.search(querys)(0));
    val que = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR");
    que.where("questionR.result.teacher=:teaId", questionnaireStat.teacher);
    que.where("questionR.result.lesson=:less", questionnaireStat.lesson);
    que.select("questionR.question.id,questionR.option.id,count(*)");
    que.groupBy("questionR.question.id,questionR.option.id");
    put("questionRs", entityDao.search(que));
    val quer = OqlBuilder.from[Array[Any]](classOf[QuestionResult].getName, "questionR");
    quer.where("questionR.result.teacher=:teaId", questionnaireStat.teacher);
    quer.where("questionR.result.lesson=:less", questionnaireStat.lesson);
    quer.select("questionR.question.id,questionR.question.content,sum(questionR.score)/count(questionR.id)");
    quer.groupBy("questionR.question.id,questionR.question.content");
    put("questionResults", entityDao.search(quer));
    forward();
  }

  /**
   * 教师个人查询自己被评教的详细情况
   *
   * @param mapping
   * @param form
   * @param request
   * @param response
   * @return @
   */
  def info(): String = {
    val id = getLong("teacherStat.id").get
    val questionnaireStat = entityDao.get(classOf[LessonEvalStat], id);

    val teaId = questionnaireStat.teacher.id
    val semesterId = questionnaireStat.semester.id
    val lessonId = questionnaireStat.lesson.id
    /** 本学期是否评教 */
    val builder = OqlBuilder.from(classOf[EvaluateResult], "evaluateResult");
    builder.where("evaluateResult.lesson.semester.id=" + semesterId);
    builder.select("distinct evaluateResult.questionnaire.id");
    val list = entityDao.search(builder);
    if (list.size == 1) {
    } else {
      redirect("search", "未找到评教记录!")
    }
    var semest: Semester = null;
    if (semesterId != 0) {
      semest = entityDao.get(classOf[Semester], semesterId);
    }
    put("semester", semest);
    var teacher: Teacher = null;
    if (teaId != 0) {
      teacher = entityDao.get(classOf[Teacher], teaId);
    }
    put("teacher", teacher);
    var lesson: Lesson = null;
    if (lessonId != 0L) {
      lesson = entityDao.get(classOf[Lesson], lessonId);
    }
    put("lesson", lesson);
    /** 院系平均分 */
    val querdep = OqlBuilder.from[Float](classOf[EvaluateResult].getName + " evaluateResult," +
      classOf[QuestionResult].getName + " questionResult");
    querdep.select("sum(questionResult.score)/count(distinct evaluateResult.id)");
    querdep.where("evaluateResult.id=questionResult.result.id");
    querdep.where("evaluateResult.lesson.semester.id=" + semesterId);
    if (lesson != null) {
      querdep.where("evaluateResult.lesson.teachDepart.id=:depId", lesson.teachDepart.id);
      // querdep.where("evaluateResult.teacher.teacher.user.department.id=:depId",teacher.getDepartment().getId());
    }
    put("depScores", entityDao.search(querdep)(0).toString().toFloat);
    /** 全校平均分 */
    val que = OqlBuilder.from[Float](classOf[EvaluateResult].getName + " evaluateResult," +
      classOf[QuestionResult].getName + " questionResult");
    que.select("sum(questionResult.score)/count(distinct evaluateResult.id)");
    que.where("evaluateResult.id=questionResult.result.id");
    que.where("evaluateResult.lesson.semester.id=" + semesterId);
    put("evaResults", entityDao.search(que)(0).toString().toFloat);

    /** 课程全校评教排名 */
    val querSch = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName + " evaluateResult," +
      classOf[QuestionResult].getName + " questionResult");
    querSch.select("evaluateResult.lesson.id,sum(questionResult.score)/count(distinct evaluateResult.id)");
    querSch.where("evaluateResult.id=questionResult.result.id");
    querSch.where("evaluateResult.lesson.semester.id=" + semesterId);
    querSch.groupBy("evaluateResult.lesson.id");
    querSch.orderBy("sum(questionResult.score)/count(distinct evaluateResult.id) desc");
    val schList = entityDao.search(querSch);
    var schNums = 0;
    schList foreach { ob =>

      if (ob(0).toString().equals(lessonId.toString())) {
        schNums += 1;
      }

    }
    //    for (int i = 0; i < schList.size(); i++) {
    //      Object[] ob = (Object[]) schList.get(i);
    //      if (ob[0].toString().equals(lessonId.toString())) {
    //        schNums = i + 1;
    //      }
    //    }
    put("schNum", schNums);
    put("schNums", schList.size);
    /** 课程院系评教排名 */
    val querydep = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult");
    querydep.select("evaluateResult.lesson.id,sum(questionResult.score)/count(distinct evaluateResult.id)");
    querydep.where("evaluateResult.id=questionResult.result.id");
    querydep.where("evaluateResult.lesson.semester.id=" + semesterId);
    if (lesson != null) {
      querdep.where("evaluateResult.lesson.teachDepart.id=:depId", lesson.teachDepart.id);
      // querdep.where("evaluateResult.teacher.teacher.user.department.id=:depId",teacher.getDepartment().getId());
    }
    querydep.groupBy("evaluateResult.lesson.id");
    querydep.orderBy("sum(questionResult.score)/count(distinct evaluateResult.id) desc");
    val depList = entityDao.search(querydep);
    var depNums = 0;
    depList foreach { ob =>

      if (ob(0).toString().equals(lessonId.toString())) {
        depNums += 1;
      }

    }
    //    for (int i = 0; i < depList.size(); i++) {
    //      Object[] ob = (Object[]) depList.get(i);
    //      if (ob[0].toString().equals(lessonId.toString())) {
    //        depNums = i + 1;
    //      }
    //    }
    put("depNum", depNums);
    put("depNums", depList.size);
    /** 教师评教总分 */
    val quer = OqlBuilder.from[Float](classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult");
    quer.select("sum(questionResult.score)/count(distinct evaluateResult.id)");
    quer.where("evaluateResult.id=questionResult.result.id");
    quer.where("evaluateResult.lesson.semester.id=" + semesterId);

    if (teaId != 0L) {
      quer.where("evaluateResult.teacher.id=:teaId", teaId);
    }
    if (lessonId != 0L) {
      quer.where("evaluateResult.lesson.id=:lessonId", lessonId);
    }
    put("teaScore", entityDao.search(quer)(0));

    /** 教师各项得分 */
    val quer1 = OqlBuilder.from[Array[Any]](classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult");
    quer1.select("questionResult.question.content,questionResult.question.id,sum(questionResult.score)/count(evaluateResult.id)");
    quer1.where("evaluateResult.id=questionResult.result.id");
    quer1.where("evaluateResult.lesson.semester.id=" + semesterId);
    if (teaId != 0L) {
      quer1.where("evaluateResult.teacher.id=:teaId", teaId);
    }
    if (lessonId != 0L) {
      quer1.where("evaluateResult.lesson.id=:lessonId", lessonId);
    }
    quer1.groupBy("questionResult.question.id,questionResult.question.content");
    // quer1.orderBy("questionResult.question.priority desc");
    put("questionRList", entityDao.search(quer1));
    /** 院系各项得分 */
    val depQuery = OqlBuilder.from(classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult");
    depQuery.select("questionResult.question.id,sum(questionResult.score)/count(evaluateResult.id)");
    depQuery.where("evaluateResult.id=questionResult.result.id");
    depQuery.where("evaluateResult.lesson.semester.id=" + semesterId);
    if (lesson != null) {
      depQuery.where("evaluateResult.lesson.teachDepart.id=:depsId", lesson.teachDepart.id);
    }
    depQuery.groupBy("questionResult.question.id");
    depQuery.orderBy("questionResult.question.id");
    put("depQRList", entityDao.search(depQuery));
    /** 全校各项得分 */
    val schQuery = OqlBuilder.from(classOf[EvaluateResult].getName + " evaluateResult,"
      + classOf[QuestionResult].getName + " questionResult");
    schQuery.select("questionResult.question.id,sum(questionResult.score)/count(evaluateResult.id)");
    schQuery.where("evaluateResult.id=questionResult.result.id");
    schQuery.where("evaluateResult.lesson.semester.id=" + semesterId);
    schQuery.groupBy("questionResult.question.id");
    schQuery.orderBy("questionResult.question.id");
    put("schQRList", entityDao.search(schQuery));
    return forward();
  }

  //  public void setQuestionnairStatService(QuestionnairStatService questionnairStatService) {
  //    this.questionnairStatService = questionnairStatService;
  //  }

}
