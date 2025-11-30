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

package org.openurp.qos.evaluation.clazz.web.action.admin

import org.beangle.commons.cdi.BindModule
import org.openurp.base.service.impl.SemesterServiceImpl
import org.openurp.qos.evaluation.app.course.service.StdEvaluateSwitchService
import org.openurp.qos.evaluation.clazz.web.action.*
import org.openurp.qos.evaluation.clazz.web.action.admin.*

class DefaultModule extends BindModule {

  override def binding(): Unit = {

    //******教务处菜单
    //               评教设置->课程问卷 --- 为课程设置问卷和评教方式（教师/课程）
    bind(classOf[QuestionnaireClazzAction])
    // 评教设置->问卷评教开关
    bind(classOf[StdEvaluateSwitchAction])
    bind(classOf[StdEvaluateSwitchService])
    // 评教设置->文字评教开关
    bind(classOf[FeedbackSwitchAction])

    //  评教管理->问卷有效性--------------查看全部学生评教问卷并设置有效或无效
    bind(classOf[EvaluateResultAction])
    // 评教管理->文字有效性 --------------确认学生文字评教（只有已确认的文字评教才能查看到回复）
    bind(classOf[FeedbackAction],classOf[FinalCommentAction])

    // ------------------------问卷回收率即时统计与查询---------------------
    //  问卷评教的回收率统计，按开课院系，按教学任务,具体到课程名称或教师姓名
    bind(classOf[EvaluateStatusStatAction])

    //-------------------------------问卷评教统计分析
    //  教师最终得分排名统计查询导出
    bind(classOf[FinalTeacherScoreAction])

    //  问卷评教统计--按任务得分统计 包括：排名??，院系任务统计，学校任务历史，学校分项统计*****教务处------yes
    bind(classOf[CourseEvalStatAction])
    //  问卷评教统计查询---按任务得分查询********院系管理员？
    bind(classOf[CourseEvalSearchAction])

    //  问卷评教统计--按课程教师统计 包括：排名??，院系任务统计，学校任务历史，学校分项统计*****教务处----排名方法有问题，没有平均分算法有问题
    bind(classOf[CourseEvalStatAction])

    //--暂不使用的页面
    //  问卷评教的各类统计:  -------  在做什么？
    bind(classOf[EvaluateStatisticsAction])

    //  查看任务问卷评教各问题类别结果及总分，+更改教师？？？+各院系比较
    bind(classOf[QuestionnaireStatAction])
    //  查看任务问卷评教各问题类别结果及总分
    bind(classOf[QuestionnaireStatSearchAction])

    bind(classOf[SemesterServiceImpl])

    bind(classOf[CourseStatAction])
    bind(classOf[CourseStatSearchAction])
  }
}
