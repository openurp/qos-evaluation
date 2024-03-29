[#ftl]
[@b.head/]
<div class="search-container">
  <div class="search-panel">
        [@b.form action="!search" name="questionnaireStatIndexForm" title="ui.searchForm" target="contentDiv" theme="search"]
            <input type="hidden" name="questionnaireStat.clazz.project.id" value="${(project.id)!}"/>
            [@b.select  name="semester.id" label="学年学期" items=semesters?sort_by("code") value=currentSemester option = "id,code" empty="..."/]
            [@b.textfields style="width:130px" names="questionnaireStat.clazz.crn;课程序号,questionnaireStat.clazz.course.code;课程代码,questionnaireStat.clazz.course.name;课程名称,questionnaireStat.teacher.user.code;教师工号,questionnaireStat.teacher.user.name;教师名称"/]
            [@b.select style="width:134px" name="questionnaireStat.clazz.teachDepart.id" label="开课院系" items=departments empty="..."/]
            [@b.select style="width:134px" name="selectTypeId" label="问题类别" items=indicators empty="..."/]
            [@b.select style="width:134px" name="selectMarkId" label="分值类型" items={'A':'优','B':'良','C':'中','D':'差'} empty="..."/]
            [@b.select style="width:134px" name="assessCriteriaId" label="对照标准" items=assessCriterias empty="..."/]
        [/@]
  </div>
  <div class="search-list">
            [@b.div id="contentDiv"/]
  </div>
</div>
<script type="text/javaScript">
    var form = document.questionnaireStatIndexForm;

    function changeSemester(){
        bg.form.addInput(form, "questionnaireStat.semester.id", $("input[name='semester.id']").val());
        bg.form.submit(form);
    }
</script>
[@b.foot/]

[#--
<table width="100%"class="frameTable">
    <tr>
    <td width="20%" valign="top" class="frameTable_view">
    <table width="100%" class="searchTable">
        <form id="searchForm" name="searchForm" method="post" target="questionnaireResults">
            <tr>
                <td colspan="4" align="center"><@text name="textEvaluation.selectCondition"/></td>
            </tr>
            <td>学年学期:</td>
               <td>
             <select name="questionnaireStat.semester.id" id="f_semester" style="width:100px" >
             <#list (semesters?sort_by("code")?reverse)?if_exists as semester>
             <option value="${semester.id}" title="${semester.schoolYear}&nbsp;${semester.name}">${semester.schoolYear}&nbsp;${semester.name}</option>
             </#list>
             <option value="">...</option>
             </select>
               </td>
          </tr>
            <tr>
                <td>开课院系</td>
                <td><@htm.i18nSelect datas=departmentList selected="" name="questionnaireStat.depart.id" style="width:100%">
                        <option value="">全部</option>
                    </@>
                </td>
        </tr>
        <tr>
            <td><@text name="attr.taskNo"/></td>
            <td><input type="text" name="questionnaireStat.taskSeqNo" style="width:100%" maxlength="32"/></td>
        </tr>
        <tr>
            <td><@text name="field.questionnaireStatistic.teacherName"/></td>
            <td><input type="text" name="questionnaireStat.teacher.name" style="width:100%" maxlength="20"/></td>
        </tr>
        <tr>
            <td><@text name="field.characterTeacher.course"/></td>
            <td><input type="text" name="questionnaireStat.course.name" style="width:100%" maxlength="20"/></td>
        </tr>
        <tr>
            <td>课程代码</td>
            <td><input type="text" name="questionnaireStat.course.code" style="width:100%" maxlength="32"/></td>
        </tr>
        <tr>
            <td>问题类别</td>
            <td><@htm.i18nSelect datas=indicatorList selected="" name="selectTypeId" style="width:100%">
                    <option value="">全部</option>
                    <option value="0">总评</option>
                </@>
            </td>
        </tr>
        <tr>
            <td>分值类型</td>
            <td><select name="selectMark" style="width:100%">
                <option value="">全部</option>
                <option value="A">优</option>
                <option value="B">良</option>
                <option value="C">中</option>
                <option value="D">差</option>
            </select>
            </td>
        </tr>
        <tr>
            <td>对照标准</td>
            <td><select name="assessCriteriaId" style="width:100%">
                <#list assessCriterias as criteria>
                <option value="${criteria.id}">${criteria.name}</option>
                </#list>
            </select>
            </td>
        </tr>
        <tr>
            <td colspan="4"  align="center">
                <input type="button" value="查询" name="buttonSubmit" onClick="search()" class="buttonStyle"/>
            </td>
        </tr>
        <tr>
            <td colspan="4"  align="center">
                <font color="red">
                   1.如果结果中问题类别成绩为空,则评教成绩属手工录入<br>2.优良中差比重：优(>=90),良(>=80),中(>=60),差(>=0)<br>
                   <#--3.2007-2008(2)学年以前的数据都是历史数据,所以有效票数都是0-->
                   </font>
            </td>
        </tr>
        </table>
        <input type="hidden" name="titles" value="开课院系,教师部门,教师代码,教师名称,教师类别,课程代码,课程序号,课程名称,有效票数,<#list indicatorList?if_exists as indicator>${indicator.name?js_string},</#list>总评成绩,学年度,学期">
        <input type="hidden" name="keys" value="depart.name,teacher.user.department.name,teacher.user.code,teacher.name,teacher.teacherType.name,course.code,taskSeqNo,course.name,validTickets,<#list indicatorList?if_exists as indicator>indicatorStat_${indicator.id},</#list>scoreDisplay,semester.schoolYear,semester.name">
        </form>
        </td>
        <td valign="top">
            <iframe name="questionnaireResults" width="100%" frameborder="0" scrolling="no"></iframe>
        </td>
        </tr>
    </table>
--]
