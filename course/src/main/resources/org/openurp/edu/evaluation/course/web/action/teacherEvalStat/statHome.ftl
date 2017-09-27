[#ftl]
[@b.head/]
[@b.toolbar title="统计评教结果"]
    bar.addBack();
[/@]
[@b.form name="statForm" title="统计评教结果"  action="!stat" theme="list"]
    [@b.select  name="semester.id" label="学年学期" items=semesters?sort_by("code") value=currentSemester option = "id,code" empty="..."/]
    <tr align="center">
        <input type="hidden" name="departIds" value=""/>
        <input type="hidden" name="educationIds" value=""/>
        [@b.submit id="btnSave" value="统计评教结果" onsubmit="doStatistic()"/]
    </tr>
    [/@]
<script type="text/javaScript">
    function doStatistic(){
        var form = document.statForm;
        form["btnSave"].disabled = true;
        bg.form.submit(form, "${b.url('!stat')}");
    }
</script>
[@b.foot/]