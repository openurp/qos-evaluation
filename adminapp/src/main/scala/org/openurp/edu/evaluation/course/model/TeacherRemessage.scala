package org.openurp.edu.evaluation.course.model

import org.openurp.edu.base.model.Student
import org.beangle.commons.collection.Collections
import org.beangle.commons.model.LongId
import java.util.Date
/**
 * 文字评教教师回复
 * 
 * @author chaostone
 */
class TeacherRemessage extends LongId  {
 /** 回复信息 */
  var  remessage:String=_

  /** 回复对象 */
//  @ManyToMany
//  @JoinTable(name = "Q_EVA_TEA_REM_STDS")
  var  students =Collections.newSet[Student]

  /** 文字评教 */
  var  textEvaluation:TextEvaluation=_

  /** 显示状态 */
  var  visible:Boolean=false
  
  var createdAt:Date=_
  
  var updatedAt:Date=_
}