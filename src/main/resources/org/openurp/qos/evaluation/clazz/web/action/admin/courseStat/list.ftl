[#ftl]
[@b.head/]
    [@b.grid items=courseEvalStats var="courseEvalStat" sortable="true"]
      [@b.gridbar]
        [#if courseEvalStats?size>0]
        bar.addItem("${b.text('action.delete')}",action.remove("确认删除?"));
         [#assign stat_first = courseEvalStats?first/]
        bar.addItem("${b.text("action.export")}",action.exportData("semester.schoolYear:学年度,semester.name:学期,crn:课程序号,course.code:课程代码,course.name:课程名称,teachDepart.name:开课院系," +
        "category.name:课程分类,teacher.staff.code:教师工号,teacher.name:教师姓名,score:总得分,grade.name:档次,tickets:参评人数,categoryRank:分类排名,departRank:开课院系排名,schoolRank:全校排名,"+
        "[#list stat_first.indicatorStats as i]indicator_score_${i.indicator.code}:${i.indicator.name}得分,indicator_rank_${i.indicator.code}:${i.indicator.name}排名[#if i_has_next],[/#if][/#list]",null,'fileName=课程评教统计'));
        [/#if]
      [/@]
        [@b.row]
            [@b.boxcol/]
            [@b.col property="crn" title="课程序号" width="7%"/]
            [@b.col property="course.code" title="课程代码" width="9%"]
                <a target="_blank" href="${webapp_base}/edu/course/info/${courseEvalStat.course.id}">${courseEvalStat.course.code}</a>
            [/@]
            [@b.col property="course.name" title="课程名称" width="20%"]
                [@b.a href="!search?courseEvalStat.semester.id="+courseEvalStat.semester.id+"&courseEvalStat.course.id="+courseEvalStat.course.id]${courseEvalStat.course.name}[/@]
            [/@]
            [@b.col property="teachDepart.name" title="开课院系" width="8%"]
              ${courseEvalStat.teachDepart.shortName!courseEvalStat.teachDepart.name}
            [/@]
            [@b.col property="category.name" title="课程分类" width="11%"]
              [#if courseEvalStat.category.name?length>8]
              <span style="font-size:0.7em">${courseEvalStat.category.name}</span>
              [#else]
              ${courseEvalStat.category.name}
              [/#if]
            [/@]
            [@b.col property="teacher.name" title="教师姓名" width="11%"]
              [@b.a href="!history?teacher.id="+courseEvalStat.teacher.id]
                  [#if courseEvalStat.teacher.name?length>5]
                  <span style="font-size:0.7em">${courseEvalStat.teacher.name}</span>
                  [#else]
                  ${courseEvalStat.teacher.name}
                  [/#if]
              [/@]
            [/@]
            [@b.col property="score" title="总得分" width="7%"]
              [@b.a href="!info?id=" +courseEvalStat.id target="_blank"]${courseEvalStat.score?string("#.00")}[/@]
            [/@]
            [@b.col property="grade.name" title="档次" width="5%"/]
            [@b.col property="departRank" title="院系排名" width="7%"/]
            [@b.col property="schoolRank" title="全校排名" width="7%"/]
            [@b.col property="tickets" title="参评人数" width="7%"/]
        [/@]
    [/@]
[@b.foot/]
