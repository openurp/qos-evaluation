[#--暂时无用--]
[#--
<#include "/template/head.ftl"/>
<BODY>
     <table id="backBar" width="100%"></table>
        <script>
           var bar = new ToolBar('backBar','清除历史评教数据',null,true,true);
           bar.addItem('开始清除','clearData()');
        </script>
  <table cellpadding="0" cellspacing="0" width="100%" border="0" class="listTable">
      <form name="initializatedForm" method="post" action="" >
      <tr>
          <td align="center" class="darkColumn" colspan="2">清除历史评教数据</td>
      </tr>
      <tr>
          <td align="center" class="grayStyle">学年度学期</td>
          <td class="brightStyle">
              <table>
              <tr>
              <td>
              <select name="semesters" MULTIPLE size="15" style="width:200px" onDblClick="JavaScript:moveSelectedOption(this.form['semesters'], this.form['selectedCalendars'])" >
            <#list semesters?sort_by("year") as semester>
                <option value="${semester.id}">[${semester.calendar.educationType.name?if_exists}/${semester.schoolYear}/${semester.name}]</option>
            </#list>
            </select>
            </td>
               <td>
            <input OnClick="JavaScript:moveSelectedOption(this.form['semesters'], this.form['selectedSemesters'])" type="button" value="&gt;">
            <br>
            <input OnClick="JavaScript:moveSelectedOption(this.form['selectedSemesters'], this.form['teachSemesters'])" type="button" value="&lt;">
            </td>
            <td>
         <select name="selectedSemesters" MULTIPLE size="15" style="width:200px;" onDblClick="JavaScript:moveSelectedOption(this.form['selectedSemesters'], this.form['teachSemesters'])">
         </select>
         </td>
         </tr>
         </table>
          </td>
      </tr>
      <tr>
          <td class="grayStyle" align="center">备注</td>
          <td class="brightStyle">初始化评教是为了清除历史的评教数据,仅仅为了加快统计速度.<br>评教之前可以不执行该操作</td>
      </tr>
      <tr>
          <td class="darkColumn" colspan="2" align="center" height="25px;"></td>
      </tr>
      </form>
  </table>
  </body>
  <script>
      var form = document.initializatedForm;
       function clearData(){
        var ids = getAllOptionValue(document.initializatedForm.selectedCalendars);
        if(ids.length<1){
            alert("请选择一些学年度学期");
            return;
        }
        if(confirm("你确定清除选择的学年度的评教历史信息吗?")){
            form.action="evaluateSwitch.action?method=clearData";
            addInput(form,"doDelete","delete");
            addInput(form,"semesterIdSeq",ids);
            form.submit();
        }
    }
  </script>
<#include "/template/foot.ftl"/>
--]
