[#ftl]
[@b.head/]
    [@b.grid items=finalComments var="finalComment" sortable="true"]
      [@b.gridbar]
        [#if finalComments?size>0]
        bar.addItem("${b.text("action.export")}",action.exportData("crn:课程序号,course.code:课程代码,course.name:课程名称," +
        "teacher.user.code:教师工号,teacher.user.name:教师姓名,contents:内容,std.user.code:学号,std.user.name:学生姓名",null,'fileName=课程文字评教'));
        [/#if]
      [/@]
        [@b.row]
            [@b.boxcol/]
            [@b.col property="crn" title="课程序号" width="7%"/]
            [@b.col property="course.code" title="课程代码" width="9%"/]
            [@b.col property="course.name" title="课程名称" width="19%"/]
            [@b.col property="teacher.user.name" title="教师姓名" width="8%"]
              [#if finalComment.teacher.user.name?length>5]
              <span style="font-size:0.7em">${finalComment.teacher.user.name}</span>
              [#else]
              ${finalComment.teacher.user.name}
              [/#if]
            [/@]
            [@b.col property="contents" title="内容" width="34%"/]
            [@b.col property="std.user.name" title="学生" width="8%"/]
            [@b.col property="updatedAt" title="反馈时间" width="10%"]${finalComment.updatedAt?string('MM-dd HH:mm')}[/@]
        [/@]
    [/@]
[@b.foot/]
