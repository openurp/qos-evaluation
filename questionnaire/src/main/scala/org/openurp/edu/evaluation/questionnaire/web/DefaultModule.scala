package org.openurp.edu.evaluation.questionnaire.web

import org.beangle.commons.inject.bind.AbstractBindModule
import org.openurp.edu.evaluation.questionnaire.service.QuestionTypeService
import org.openurp.edu.evaluation.questionnaire.web.action.QuestionTypeAction
import org.openurp.edu.evaluation.questionnaire.web.action.QuestionnaireAction
import org.openurp.edu.evaluation.questionnaire.web.action.EvaluationCriteriaAction
import org.openurp.edu.evaluation.questionnaire.web.action.EvaluationConfigAction
import org.openurp.edu.evaluation.questionnaire.web.action.OptionGroupAction
import org.openurp.edu.evaluation.questionnaire.web.action.QuestionAction
class DefaultModule extends AbstractBindModule {

  override def binding() {
    
//*******教务处  评教设置——>问卷设置
    bind(classOf[EvaluationConfigAction])
//  问卷、问题、问题类别
    bind(classOf[QuestionnaireAction],classOf[QuestionAction],classOf[QuestionTypeAction],classOf[QuestionTypeService])
//  选项组、评价标准
    bind(classOf[OptionGroupAction],classOf[EvaluationCriteriaAction])
  }

}