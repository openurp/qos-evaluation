[#ftl]
[@b.head/]
[@b.toolbar title="文字评教开关设置"]bar.addBack();[/@]
  [#assign sa][#if feedbackSwitch.persisted]!update?id=${feedbackSwitch.id}[#else]!save[/#if][/#assign]
[@b.form action=sa title="文字评教开关" theme="list"  enctype="multipart/form-data"]
    [@base.semester  name="feedbackSwitch.semester.id" label="学年学期" value=feedbackSwitch.semester/]
    [@b.select name="feedbackSwitch.project.id" label="教学项目" items=projects?sort_by("id") option = "name" value =feedbackSwitch.project empty="..."/]
    [@b.select label="教师查询" required="true" name="feedbackSwitch.openedTeacher" value=((feedbackSwitch.openedTeacher)?string("1","0"))! items={'1':'开放','0':'关闭'} empty="..." /]
    [@b.select label="不限时开放学生文字评教" name="feedbackSwitch.textEvaluateOpened"  items={'1':'开放','0':'关闭'}  value=((feedbackSwitch.textEvaluateOpened)?string("1","0"))! required="true"  empty="..." /]
    [@b.startend label="开始结束时间"
    name="feedbackSwitch.beginAt,feedbackSwitch.endAt" required="true,true"
    start=feedbackSwitch.beginAt end=feedbackSwitch.endAt format="yyyy-MM-dd HH:mm" /]
    [@b.formfoot]
        [#if feedbackSwitch.persisted]<input type="hidden" name="feedbackSwitch.id" value="${feedbackSwitch.id!}" />[/#if]
        [@b.reset/]&nbsp;&nbsp;[@b.submit value="action.submit"/]
    [/@]
[/@]
<script type="text/javaScript">
    $("select[name='feedbackSwitch.openedTeacher']").parent().css("height","40px");
    $("select[name='feedbackSwitch.textEvaluateOpened']").parent().css("height","50px");
</script>
[@b.foot/]
