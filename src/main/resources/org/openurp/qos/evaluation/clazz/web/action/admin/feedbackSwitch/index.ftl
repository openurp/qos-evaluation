[#ftl]
[@b.head /]
[@b.toolbar title='文字评教开关' id='feedbackSwitchBar' /]
<div class="search-container">
  <div class="search-panel">
        [@b.form name="feedbackSwitchIndexForm" action="!search" target="contentDiv" theme="search"]
        [@base.semester  name="semester.id" label="学年学期" value=currentSemester/]
        [@b.select  name="opened" label="开关状态" items={'1':'开放','0':'关闭'} value='1'  style="width:100px"/]
        <input type="hidden" name="orderBy" value="feedbackSwitch.id"/>
        [/@]
  </div>
  <div class="search-list">
            [@b.div id="contentDiv"  href="!search?&semester.id=${(semester.id)!}" /]
  </div>
</div>
[@b.foot/]
